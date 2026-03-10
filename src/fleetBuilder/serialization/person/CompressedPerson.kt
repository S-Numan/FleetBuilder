package fleetBuilder.serialization.person

import com.fs.starfarer.api.characters.FullName
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.joinSep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.roundToDecimals

object CompressedPerson {
    fun isCompressedPerson(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('p', ignoreCase = true) == true
    }

    fun extractPersonDataFromCompString(comp: String): DataPerson.ParsedPersonData? {
        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return null

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)

        val fullData = when (metaVersion) {
            "p0" -> {
                val compressedData = comp.substring(metaIndexEnd + 1)
                CompressionUtil.decompressString(compressedData)
            }
            "P0" -> comp.substring(metaIndexEnd + 1)
            else -> return null
        } ?: return null

        val fields = fullData.split(fieldSep)

        try {
            val aiCoreId = fields[0]
            val first = fields[1]
            val last = fields[2]

            val gender = try {
                FullName.Gender.valueOf(fields[3])
            } catch (e: Exception) {
                FullName.Gender.ANY
            }

            val portrait = fields[4].ifBlank { null }

            val tags = fields[5].takeIf { it.isNotBlank() }?.split(sep)?.toSet() ?: emptySet()

            val rankId = fields[6]
            val postId = fields[7]
            val personality = fields[8]

            val level = fields[9].toInt()

            val skills = fields[10].takeIf { it.isNotBlank() }?.split(sep)?.mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2)
                    parts[0] to parts[1].toFloat()
                else null
            }?.toMap() ?: emptyMap()

            val xp = fields[11].toLong()
            val bonusXp = fields[12].toLong()
            val points = fields[13].toInt()

            val memKeys = fields.getOrNull(14)
                ?.takeIf { it.isNotBlank() }
                ?.split(sep)
                ?.mapNotNull {
                    val parts = it.split(joinSep, limit = 2)
                    if (parts.size == 2)
                        parts[0] to parts[1]
                    else null
                }?.toMap() ?: emptyMap()

            return DataPerson.ParsedPersonData(
                aiCoreId = aiCoreId,
                first = first,
                last = last,
                gender = gender,
                portrait = portrait,
                tags = tags,
                rankId = rankId,
                postId = postId,
                personality = personality,
                level = level,
                skills = skills,
                xp = xp,
                bonusXp = bonusXp,
                points = points,
                memKeys = memKeys
            )
        } catch (e: Exception) {
            showError("Error parsing person data", e)
            return null
        }
    }

    @JvmOverloads
    fun savePersonToCompString(
        data: DataPerson.ParsedPersonData,
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {
        val structureVersion =
            if (compress) "p0"
            else "P0"

        val ver = "$metaSep$structureVersion$metaSep"

        val parts = mutableListOf<String>()

        parts += data.aiCoreId
        parts += data.first
        parts += data.last
        parts += data.gender.name
        parts += (data.portrait ?: "")
        parts += data.tags.joinToString(sep)
        parts += data.rankId
        parts += data.postId
        parts += data.personality
        parts += data.level.toString()

        val skillString = data.skills.entries.joinToString(sep) {
            "${it.key}:${it.value}"
        }

        parts += skillString
        parts += data.xp.roundToDecimals(2).toString()
        parts += data.bonusXp.roundToDecimals(2).toString()
        parts += data.points.toString()

        val memKeyString = data.memKeys.entries.mapNotNull { entry ->
            val key = entry.key
            val value = entry.value

            val formattedValue = when (value) {
                is Boolean -> value.toString()
                is Int -> value.toString()
                is String -> value
                is Float -> value.roundToDecimals(2).toString()
                is Long -> value.roundToDecimals(2).toString()
                else -> null // exclude unsupported types
            }

            formattedValue?.let { "$key$joinSep$it" }
        }.joinToString(sep)

        parts += memKeyString

        var personString = parts.joinToString(fieldSep)

        if (compress)
            personString = CompressionUtil.compressString(personString)

        personString = "$ver$personString"

        if (includePrepend) {
            val readable = "${data.first} ${data.last} : "
            personString = readable + personString
        }

        return personString
    }
}