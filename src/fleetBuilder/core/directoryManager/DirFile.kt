package fleetBuilder.core.directoryManager

import com.fs.starfarer.api.Global

internal class DirFile internal constructor(
    inputPath: String,
    override val manager: DirectoryManager? = resolveManager(normalizeName(inputPath))
) : DirPath(filePath = normalizeName(inputPath), manager = manager) {
    val fileName = filePath.substringAfterLast('/')

    fun read(): String? {
        if (!exists()) return null

        return try {
            settings.readTextFileFromCommon(filePath)
        } catch (ex: Exception) {
            Global.getLogger(this.javaClass).error("Failed to read file: $filePath (${ex.message})")
            null
        }
    }

    fun write(contents: String) {
        settings.writeTextFileToCommon(filePath, contents)
    }
}