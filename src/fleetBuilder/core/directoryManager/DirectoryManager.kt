package fleetBuilder.core.directoryManager

import fleetBuilder.util.api.kotlin.optJSONArrayToStringList
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal class DirectoryManager private constructor(
    inputPath: String,
    override val manager: DirectoryManager?
) : DirPath(
    filePath = inputPath,
    manager = manager
) {
    companion object {
        const val CONFIG_FILE_NAME = ".directory" // TODO, confirm if creating a file with a dot at the start actually works.

        private fun normalizeConfigPath(path: String): String {
            val normalized = path.replace("\\", "/")

            val base = normalized
                .removeSuffix(".data")
                .removeSuffix(CONFIG_FILE_NAME)

            return if (base.endsWith("/"))
                "$base$CONFIG_FILE_NAME"
            else
                "$base/$CONFIG_FILE_NAME"
        }

        private val instances =
            ConcurrentHashMap<String, WeakReference<DirectoryManager>>()

        private fun removeInstance(path: String) {
            instances.remove(path)
        }

        fun get(inputPath: String): DirectoryManager {
            cleanup()

            val normalized = normalizeConfigPath(inputPath)

            // Try existing
            val existing = instances[normalized]?.get()
            if (existing != null) return existing

            // Create new
            val created = DirectoryManager(
                normalized,
                getParentManager(normalized)
            )

            instances[normalized] = WeakReference(created)
            return created
        }

        private fun cleanup() {
            instances.entries.removeIf { it.value.get() == null }
        }

        private fun getParentManager(path: String): DirectoryManager? {
            val parentPath = path
                .removeSuffix(".data")
                .removeSuffix("/$CONFIG_FILE_NAME")
                .substringBeforeLast('/', "")
                .takeIf { it.isNotEmpty() }

            return parentPath?.let { get(it) }
        }
    }

    init {
        require(filePath.endsWith(CONFIG_FILE_NAME)) {
            "Directory config must end with '$CONFIG_FILE_NAME': $filePath"
        }
    }

    val folderPath: String = filePath.removeSuffix(CONFIG_FILE_NAME)
    val folderName = folderPath.substringAfterLast('/')

    private val _containingPaths: MutableList<DirPath> by lazy {
        loadPaths().toMutableList()
    }

    val containingPaths: List<DirPath>
        get() = _containingPaths

    // -------------------------
    // Loading
    // -------------------------

    private fun loadPaths(): List<DirPath> {
        return if (settings.fileExistsInCommon(filePath)) {
            readConfigFromFile()
        } else {
            saveEmptyConfig()
            emptyList()
        }
    }

    internal fun readConfigFromFile(): List<DirPath> {
        val json = settings.readJSONFromCommon(filePath, false)

        require(json.has("paths")) {
            "Invalid directory config file at '$filePath'"
        }

        return json.optJSONArrayToStringList("paths").map { entry ->
            val fullPath = folderPath + entry

            if (entry.endsWith("/"))
                DirectoryManager.get(fullPath)
            else
                DirFile(fullPath, this)
        }
    }

    private fun saveEmptyConfig() {
        val json = JSONObject().put("paths", JSONArray())
        settings.writeJSONToCommon(filePath, json, false)
    }

    internal fun saveConfigToFile() {
        val json = JSONObject()
        val pathsArray = JSONArray()

        _containingPaths.forEach {
            pathsArray.put(it.filePath.substringAfterLast("/"))
        }

        json.put("paths", pathsArray)
        settings.writeJSONToCommon(filePath, json, false)
    }

    // -------------------------
    // Mutations
    // -------------------------

    fun createFolder(folderName: String): DirectoryManager {
        val parts = normalizeName(folderName)
            .split("/")
            .filter { it.isNotEmpty() }

        var current = this

        for (part in parts) {
            val newPath = current.folderPath + part + "/"

            val existing = current._containingPaths
                .filterIsInstance<DirectoryManager>()
                .find { it.folderPath == newPath }

            current = existing ?: DirectoryManager.get(newPath).also {
                current._containingPaths.add(it)
                current.saveConfigToFile()
            }
        }

        return current
    }

    @JvmOverloads
    fun writeFile(fileName: String, fileContents: String = ""): DirFile {
        require(fileName != CONFIG_FILE_NAME) {
            "Cannot use reserved name '$CONFIG_FILE_NAME'"
        }
        require('/' !in fileName && '\\' !in fileName) {
            "File name must not contain slashes: $fileName"
        }

        val existing = _containingPaths.find { it.filePath.endsWith(fileName) }
        if (existing is DirFile) {
            existing.write(fileContents)
            return existing
        }

        return DirFile(folderPath + fileName, this).also {
            it.write(fileContents)
            _containingPaths.add(it)
            saveConfigToFile()
        }
    }

    internal fun removeContainingPath(entry: DirPath) {
        if(_containingPaths.remove(entry))
            saveConfigToFile()
    }

    override fun delete(): Boolean {
        val exists = exists()

        if(exists) {
            _containingPaths.toList().forEach { it.delete() }
        }
        // TODO: remove now empty folder once starsector 0.98.5 comes out with the remove folder API

        val deleted = super.delete()
        removeInstance(filePath)

        return deleted
    }
}