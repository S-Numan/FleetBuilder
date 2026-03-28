package fleetBuilder.serialization.person

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.joinSep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.serialization.person.DataPerson.buildPersonFull
import fleetBuilder.serialization.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.util.FBTxt
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.roundToDecimals
import java.util.Random

object CompressedPerson {
    fun isCompressedPerson(comp: String): Boolean {
        val metaIndexStart = comp.indexOf(metaSep)
        if (metaIndexStart == -1) return false

        val metaVersion = comp.getOrNull(metaIndexStart + 1)
        return metaVersion?.equals('p', ignoreCase = true) == true
    }

    @JvmOverloads
    fun getPersonFromCompString(
        comp: String,
        settings: PersonSettings = PersonSettings(),
        missing: MissingElements = MissingElements(),
        random: Random = Random()
    ): PersonAPI {
        val parsed = extractPersonDataFromCompString(comp, missing) ?: run {
            DataPerson.ParsedPersonData()
        }

        return buildPersonFull(parsed, settings, missing, random)
    }

    @JvmOverloads
    fun extractPersonDataFromCompString(
        comp: String,
        missing: MissingElements = MissingElements()
    ): DataPerson.ParsedPersonData? {

        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return null

        val metaVersion = comp.substring(metaIndexStart + 1, metaIndexEnd)

        val fullData = when (metaVersion) {
            "p0" -> {
                val compressedData = comp.substring(metaIndexEnd + 1)
                CompressionUtil.base64Inflate(compressedData)
            }
            "P0" -> comp.substring(metaIndexEnd + 1)
            else -> return null
        } ?: return null

        if (fullData.isBlank())
            return null

        try {
            val firstFieldSep = fullData.indexOf(fieldSep)
            if (firstFieldSep == -1)
                return null

            val modInfoBulk = fullData.substring(0, firstFieldSep)

            val parts = modInfoBulk.split(sep)

            val modInfos = parts.chunked(3).map { it.joinToString(sep) }

            val gameMods: Set<GameModInfo> =
                modInfos.mapNotNull { mod ->
                    val p = mod.split(sep)

                    if (p.size == 3) {
                        val (id, name, ver) = p
                        GameModInfo(id, name, ver)
                    } else null
                }.toSet()

            missing.gameMods.addAll(gameMods)

            // Person Data

            val dataString = fullData.substring(firstFieldSep + 1)

            val fields = dataString.split(fieldSep)

            val aiCoreId = fields[0]
            val first = fields[1]
            val last = fields[2]

            val gender = try {
                FullName.Gender.valueOf(fields[3])
            } catch (e: Exception) {
                FullName.Gender.ANY
            }

            val portrait = fields[4].ifBlank { null }

            val tags =
                fields[5].takeIf { it.isNotBlank() }
                    ?.split(sep)
                    ?.toSet()
                    ?: emptySet()

            val personality = fields[6]

            val level = fields[7].toInt()

            val skills =
                fields[8].takeIf { it.isNotBlank() }
                    ?.split(sep)
                    ?.mapNotNull {
                        val p = it.split(joinSep)
                        if (p.size == 2)
                            p[0] to p[1].toFloat()
                        else null
                    }?.toMap()
                    ?: emptyMap()

            val rankId = fields[9]
            val postId = fields[10]

            val xp = fields[11].toLong()
            val bonusXp = fields[12].toLong()
            val points = fields[13].toInt()

            val memKeys =
                fields.getOrNull(14)
                    ?.takeIf { it.isNotBlank() }
                    ?.split(sep)
                    ?.mapNotNull {
                        val p = it.split(joinSep, limit = 2)
                        if (p.size == 2) {
                            val key = "$" + p[0]
                            val raw = p[1]

                            val value: Any =
                                raw.toIntOrNull()
                                    ?: raw.toLongOrNull()
                                    ?: raw.toFloatOrNull()
                                    ?: raw.toDoubleOrNull()
                                    ?: raw.lowercase().toBooleanStrictOrNull()
                                    ?: raw

                            key to value
                        } else null
                    }
                    ?.toMap()
                    ?: emptyMap()

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
        person: PersonAPI,
        settings: PersonSettings = PersonSettings(),
        includePrepend: Boolean = true,
        includeModInfo: Boolean = true,
        compress: Boolean = true
    ): String {
        return savePersonToCompString(getPersonDataFromPerson(person, settings), includePrepend = includePrepend, includeModInfo = includeModInfo, compress = compress)
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

        // Core identity
        parts += data.aiCoreId
        parts += data.first
        parts += data.last
        parts += data.gender.name
        parts += (data.portrait ?: "")
        parts += data.tags.joinToString(sep)

        // Personality
        parts += data.personality

        // Level
        parts += data.level.toString()

        // Skills
        val skillString = data.skills.entries.joinToString(sep) {
            "${it.key}$joinSep${it.value}"
        }
        parts += skillString

        // Rank / Post
        parts += data.rankId
        parts += data.postId

        // Stats
        parts += data.xp.toString()
        parts += data.bonusXp.toString()
        parts += data.points.toString()

        // Memory keys
        val memKeyString = data.memKeys.entries.mapNotNull { entry ->
            val key = entry.key
            val value = entry.value

            val formattedValue = when (value) {
                is Float -> value.roundToDecimals(2).toString()
                is Double -> value.roundToDecimals(2).toString()
                is Boolean, is Int, is Long -> value.toString()
                is String -> value
                else -> null
            }

            formattedValue?.let { "${key.removePrefix("$")}$joinSep$it" }
        }.joinToString(sep)

        parts += memKeyString

        var personString = parts.joinToString(fieldSep)

        var requiredMods = ""
        var addedModDetails = ""

        if (includeModInfo) {
            val mods = mutableSetOf<GameModInfo>()

            for (skill in data.skills.keys) {
                val spec = Global.getSettings().getSkillSpec(skill) ?: continue
                val modSpec = spec.sourceMod
                if (modSpec != null) {
                    mods += GameModInfo(
                        modSpec.id,
                        modSpec.name,
                        modSpec.version
                    )
                }
            }

            if (mods.isNotEmpty()) {
                requiredMods = FBTxt.txt("mods_used_prefix")

                for (mod in mods) {

                    addedModDetails += "${mod.id}$sep${mod.name}$sep${mod.version}$sep"

                    requiredMods += "(${mod.name}) $sep "
                }

                requiredMods = requiredMods.dropLast(3)
                addedModDetails = addedModDetails.dropLast(1)
            }
        }

        // Prepend mod metadata to data block
        personString = "$addedModDetails$fieldSep$personString"

        // Compression
        if (compress)
            personString = CompressionUtil.base64Deflate(personString)

        personString = "$ver$personString"

        // Readable prepend
        if (includePrepend) {
            val readable = "${data.first} ${data.last} : $requiredMods"
            personString = readable + personString
        }

        return personString
    }
}