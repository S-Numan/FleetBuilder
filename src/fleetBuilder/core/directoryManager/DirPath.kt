package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global

open class DirPath(
    open val path: String,
    open val manager: DirectoryManager?
) {
    open fun delete() {

    }

    private fun findManager(): DirectoryManager? {
        if (manager != null)
            return manager

        val prevPath = path.substringBeforeLast("/", "") + "/"
        if (prevPath == "/")
            return null

        return DirectoryManager(prevPath)
    }
}

class DirFile(
    override val path: String,
    override val manager: DirectoryManager
) : DirPath(path = path, manager = manager) {
    init {
        if (path.endsWith("/"))
            throw IllegalArgumentException("Path must not end with a slash.")
    }

    fun read(): String? {
        return try {
            if (Global.getSettings().fileExistsInCommon(path))
                Global.getSettings().readTextFileFromCommon(path)
            else
                null
        } catch (_: Exception) {
            null
        }
    }

    fun write(contents: String) {
        Global.getSettings().writeTextFileToCommon(path, contents)
    }

    override fun delete() {
        Global.getSettings().deleteTextFileFromCommon(path)
        manager.containingPaths.remove(this)

        manager.saveConfigToFile()
    }
}