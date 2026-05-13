package fleetBuilder.core.directoryManager

import fleetBuilder.util.api.kotlin.optJSONArrayToStringList
import org.json.JSONArray
import org.json.JSONObject

const val CONFIG_FILE_NAME = "directory"

class DirectoryManager(
    inputPath: String,
    override val manager: DirectoryManager? = resolveManager(normalizeConfigPath(inputPath).removeSuffix(CONFIG_FILE_NAME))
) : DirPath(
    path = normalizeConfigPath(inputPath),
    manager = manager
) {
    companion object {
        private fun normalizeConfigPath(path: String): String {
            val normalized = path.replace("\\", "/")

            val base = normalized
                .removeSuffix(".data")
                .removeSuffix(CONFIG_FILE_NAME)

            return if (base.endsWith("/"))
                base + CONFIG_FILE_NAME
            else
                "$base/$CONFIG_FILE_NAME"
        }
    }

    init {
        require(path.endsWith(CONFIG_FILE_NAME)) {
            "Directory config must end with '$CONFIG_FILE_NAME': $path"
        }
    }

    val folderPath: String = path.removeSuffix(CONFIG_FILE_NAME)

    private val _containingPaths: MutableList<DirPath> by lazy {
        loadPaths().toMutableList()
    }

    val containingPaths: List<DirPath>
        get() = _containingPaths

    // -------------------------
    // Loading
    // -------------------------

    private fun loadPaths(): List<DirPath> {
        return if (settings.fileExistsInCommon(path)) {
            readConfigFromFile()
        } else {
            saveEmptyConfig()
            emptyList()
        }
    }

    internal fun readConfigFromFile(): List<DirPath> {
        val json = settings.readJSONFromCommon(path, false)

        require(json.has("paths")) {
            "Invalid directory config file at '$path'"
        }

        return json.optJSONArrayToStringList("paths").map { entry ->
            val fullPath = folderPath + entry

            if (entry.endsWith("/"))
                DirectoryManager(fullPath, this)
            else
                DirFile(fullPath, this)
        }
    }

    private fun saveEmptyConfig() {
        val json = JSONObject().put("paths", JSONArray())
        settings.writeJSONToCommon(path, json, false)
    }

    internal fun saveConfigToFile() {
        val json = JSONObject()
        val pathsArray = JSONArray()

        _containingPaths.forEach {
            pathsArray.put(it.path.substringAfterLast("/"))
        }

        json.put("paths", pathsArray)
        settings.writeJSONToCommon(path, json, false)
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

            current = existing ?: DirectoryManager(newPath, current).also {
                current._containingPaths.add(it)
                current.saveConfigToFile()
            }
        }

        return current
    }

    fun createFile(fileName: String, fileContents: String): DirFile {
        require(fileName != CONFIG_FILE_NAME) {
            "Cannot use reserved name '$CONFIG_FILE_NAME'"
        }
        require('/' !in fileName && '\\' !in fileName) {
            "File name must not contain slashes: $fileName"
        }

        val existing = _containingPaths.find { it.path.endsWith(fileName) }
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

    internal fun remove(entry: DirPath) {
        _containingPaths.remove(entry)
        saveConfigToFile()
    }

    override fun delete() {
        _containingPaths.toList().forEach { it.delete() }
        settings.deleteTextFileFromCommon(path)
        // TODO: remove now empty folder once starsector 0.98.5 comes out with the remove folder API
        manager?.remove(this)
    }
}