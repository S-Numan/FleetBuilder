package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global
import fleetBuilder.util.api.kotlin.optJSONArrayToStringList
import org.json.JSONArray
import org.json.JSONObject

const val CONFIG_FILE_NAME = "directory"

data class DirectoryManager(
    private val inputPath: String,
    override val manager: DirectoryManager? = null,
    val folderPath: String = inputPath.removeSuffix(".data").removeSuffix(CONFIG_FILE_NAME).replace("\\", "/"),
    val configFilePath: String = folderPath + CONFIG_FILE_NAME,
) : DirPath(path = configFilePath, manager = manager) {

    init {
        if (!folderPath.endsWith("/"))
            throw IllegalArgumentException("Folder path must end with a slash.")
    }

    val containingPaths: MutableList<DirPath> by lazy {
        val list = mutableListOf<DirPath>()
        generatePathsInto(list)
        list
    }

    private fun generatePathsInto(list: MutableList<DirPath>) {
        if (Global.getSettings().fileExistsInCommon(configFilePath))
            readConfigFromFile(list)
        else
            saveConfigToFile()
    }

    internal fun readConfigFromFile(list: MutableList<DirPath>) {
        val json = Global.getSettings().readJSONFromCommon(configFilePath, false)
        if (!json.has("paths"))
            throw IllegalArgumentException("Invalid directory config file at '$configFilePath'")

        val paths = json.optJSONArrayToStringList("paths")

        list.clear()

        paths.forEach {
            val dirPath = if (it.endsWith('/'))
                DirectoryManager(folderPath + it, this)
            else
                DirFile(folderPath + it, this)

            containingPaths.add(dirPath)
        }
    }

    internal fun saveConfigToFile() {
        val json = JSONObject()
        val pathsArray = JSONArray()
        containingPaths.forEach {
            pathsArray.put(it.path)
        }
        json.put("paths", pathsArray)

        Global.getSettings().writeJSONToCommon(folderPath + CONFIG_FILE_NAME, json, false)
    }

    fun createFolder(folderName: String): DirectoryManager {
        val cleaned = folderName.replace("\\", "/").trim('/')

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
        if (fileName == CONFIG_FILE_NAME)
            throw IllegalArgumentException("Cannot create file with name '$CONFIG_FILE_NAME'")
        if (fileName.contains("/") || fileName.contains("\\"))
            throw IllegalArgumentException("File name cannot contain slashes '$fileName'")

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

    override fun delete() {
        containingPaths.forEach {
            it.delete()
        }
        Global.getSettings().deleteTextFileFromCommon(configFilePath)
        // TODO: Remove now now empty folder when this is added to starsector in 0.98.5
        manager?.containingPaths?.remove(this)
    }
}