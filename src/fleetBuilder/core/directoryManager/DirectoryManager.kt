package fleetBuilder.core.directoryManager

import fleetBuilder.util.api.kotlin.optJSONArrayToStringList
import org.json.JSONArray
import org.json.JSONObject

const val CONFIG_FILE_NAME = "directory"

class DirectoryManager(
    inputPath: String,
    override var manager: DirectoryManager? = null
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

        private fun normalizeName(name: String): String {
            return name
                .replace("\\", "/")
                .trim('/')
        }
    }

    init {
        require(path.endsWith("/")) {
            "Folder path must end with '/': $path"
        }

        manager = resolveManager()
    }

    val folderPath: String = path.removeSuffix(CONFIG_FILE_NAME)

    private val containingPaths: MutableList<DirPath> by lazy {
        val list = mutableListOf<DirPath>()
        generatePathsInto(list)
        list
    }

    private fun generatePathsInto(list: MutableList<DirPath>) {
        if (settings.fileExistsInCommon(path))
            readConfigFromFile(list)
        else
            saveConfigToFile()
    }

    internal fun readConfigFromFile(list: MutableList<DirPath>) {
        val json = settings.readJSONFromCommon(path, false)
        if (!json.has("paths"))
            throw IllegalArgumentException("Invalid directory config file at '$path'")

        val paths = json.optJSONArrayToStringList("paths")

        list.clear()

        paths.forEach {
            val dirPath = if (it.endsWith('/'))
                DirectoryManager(folderPath + it, this)
            else
                DirFile(folderPath + it, this)

            list.add(dirPath)
        }
    }

    internal fun saveConfigToFile() {
        val json = JSONObject()
        val pathsArray = JSONArray()
        containingPaths.forEach {
            pathsArray.put(it.path.substringAfterLast("/"))
        }
        json.put("paths", pathsArray)

        settings.writeJSONToCommon(folderPath + CONFIG_FILE_NAME, json, false)
    }

    // Recursively create DirectoryManager if needed
    fun createFolder(folderName: String): DirectoryManager {
        val cleaned = normalizeName(folderName)

        // Split into parts: "a/b/c" -> ["a", "b", "c"]
        val parts = cleaned.split("/").filter { it.isNotEmpty() }

        var current = this

        for (part in parts) {
            val newPath = current.folderPath + part + "/"

            // Check if already exists in containingPaths
            val existing = current.containingPaths
                .filterIsInstance<DirectoryManager>()
                .find { it.folderPath == newPath }

            current = existing ?: DirectoryManager(newPath, current)
        }

        current.containingPaths.getOrNull(0) // To call the lazy variable, creating the folder.
        return current
    }

    fun createFile(fileName: String, fileContents: String): DirFile {
        require(fileName != CONFIG_FILE_NAME) {
            "Cannot use reserved name '$CONFIG_FILE_NAME'"
        }
        require(!fileName.contains("/") && !fileName.contains("\\")) {
            "File name must not contain slashes: $fileName"
        }

        val existing = containingPaths.find { it.path.endsWith(fileName) }
        if (existing != null) {
            (existing as DirFile).write(fileContents)
            return existing
        }

        val dirPath = DirFile(folderPath + fileName, this)
        dirPath.write(fileContents)

        containingPaths.add(dirPath)
        saveConfigToFile()

        return dirPath
    }

    internal fun remove(entry: DirPath) {
        containingPaths.remove(entry)
        saveConfigToFile()
    }

    override fun delete() {
        containingPaths.toList().forEach {
            it.delete()
        }
        settings.deleteTextFileFromCommon(path)

        manager?.remove(this)
    }
}