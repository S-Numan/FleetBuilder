package fleetBuilder.core.util.file.directory

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI

internal open class DirPath protected constructor(
    open val filePath: String,
    open val manager: DirectoryManager?
) {
    companion object {
        fun resolveManager(path: String): DirectoryManager? {
            val parent = path.substringBeforeLast('/', "")
                .takeIf { it.isNotEmpty() }
                ?.let { "$it/" }

            return parent?.let { DirectoryManager.get(it) }
        }

        fun normalizeName(name: String): String {
            return name
                .replace("\\", "/")
                .trim('/')
        }
    }

    protected val settings: SettingsAPI get() = Global.getSettings()

    /**
     * Deletes the file or folder at the path.
     * @return true if the file or folder was deleted, false otherwise.
     */
    open fun delete(): Boolean {
        val exists = exists()
        if (exists)
            settings.deleteTextFileFromCommon(filePath)

        manager?.removeContainingPath(this)
        return exists
    }

    /**
     * Checks if the file or folder exists.
     * @return true if the file or folder exists, false otherwise.
     */
    open fun exists(): Boolean {
        return settings.fileExistsInCommon(filePath)
    }
}