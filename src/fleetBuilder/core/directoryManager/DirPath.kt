package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI

open class DirPath(
    open val path: String,
    open val manager: DirectoryManager?
) {
    companion object {
        fun resolveManager(path: String): DirectoryManager? {
            val parent = path.substringBeforeLast('/', "")
                .takeIf { it.isNotEmpty() }
                ?.let { "$it/" }

            return parent?.let { DirectoryManager(it) }
        }

        fun normalizeName(name: String): String {
            return name
                .replace("\\", "/")
                .trim('/')
        }
    }

    protected val settings: SettingsAPI get() = Global.getSettings()

    open fun delete() {

    }
}

class DirFile(
    inputPath: String,
    override val manager: DirectoryManager? = resolveManager(normalizeName(inputPath))
) : DirPath(path = normalizeName(inputPath), manager = manager) {

    fun exists(): Boolean {
        return settings.fileExistsInCommon(path)
    }

    fun read(): String? {
        if (!exists()) return null

        return try {
            settings.readTextFileFromCommon(path)
        } catch (ex: Exception) {
            Global.getLogger(this.javaClass).error("Failed to read file: $path (${ex.message})")
            null
        }
    }

    fun write(contents: String) {
        settings.writeTextFileToCommon(path, contents)
    }

    override fun delete() {
        if (exists()) {
            settings.deleteTextFileFromCommon(path)
        }
        manager?.remove(this)
    }
}