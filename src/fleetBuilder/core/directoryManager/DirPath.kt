package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI

internal open class DirPath protected constructor(
    open val path: String,
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

    open fun delete() {

    }
}