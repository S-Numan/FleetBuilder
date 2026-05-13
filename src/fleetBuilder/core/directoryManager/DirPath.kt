package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI

open class DirPath(
    open val path: String,
    open var manager: DirectoryManager?
) {
    protected val settings: SettingsAPI get() = Global.getSettings()

    open fun delete() {

    }

    protected fun resolveManager(): DirectoryManager? {
        manager?.let { return it }

        val parent = path.substringBeforeLast('/', "")
            .takeIf { it.isNotEmpty() }
            ?.let { "$it/" }

        return parent?.let { DirectoryManager(it) }
    }
}

class DirFile(
    override val path: String,
    override var manager: DirectoryManager?
) : DirPath(path = path, manager = manager) {
    init {
        require(!path.endsWith("/")) {
            "File path must not end with '/': $path"
        }
    }

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