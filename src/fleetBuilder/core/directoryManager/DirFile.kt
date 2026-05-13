package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global

internal class DirFile(
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