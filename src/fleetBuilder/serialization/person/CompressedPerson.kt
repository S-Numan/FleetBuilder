package fleetBuilder.serialization.person

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import fleetBuilder.core.FBTxt
import fleetBuilder.core.displayMessage.DisplayMessage.showError
import fleetBuilder.serialization.GameModInfo
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.SerializationUtils.fieldSep
import fleetBuilder.serialization.SerializationUtils.joinSep
import fleetBuilder.serialization.SerializationUtils.memKeyJoinSep
import fleetBuilder.serialization.SerializationUtils.memKeySep
import fleetBuilder.serialization.SerializationUtils.metaSep
import fleetBuilder.serialization.SerializationUtils.personSep
import fleetBuilder.serialization.SerializationUtils.sep
import fleetBuilder.serialization.person.DataPerson.buildPersonFull
import fleetBuilder.serialization.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.util.lib.CompressionUtil
import fleetBuilder.util.lib.PrefixedCodec
import java.util.*

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
        missing: MissingContent = MissingContent(),
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
        missing: MissingContent = MissingContent()
    ): DataPerson.ParsedPersonData? {

        val metaIndexStart = comp.indexOf(metaSep)
        val metaIndexEnd = comp.indexOf(metaSep, metaIndexStart + 1)

        if (metaIndexStart == -1 || metaIndexEnd == -1)
            return null

        val metaVersionFull = comp.substring(metaIndexStart + 1, metaIndexEnd)
        if (!metaVersionFull.lowercase().startsWith('p'))
            return null
        val metaVersionNumber = metaVersionFull.substring(1).toInt()
        val metaVersionCompressed = metaVersionFull.startsWith('p')

        val fullData = if (metaVersionCompressed) {
            val compressedData = comp.substring(metaIndexEnd + 1)
            CompressionUtil.base64Inflate(compressedData)
        } else {
            comp.substring(metaIndexEnd + 1)
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

            val fields = dataString.split(
                fieldSep,
                limit = if (metaVersionNumber < 2) 15
                else 14
            )

            var cur = 0

            val aiCoreId = fields[cur]
            cur++
            val first = fields[cur]
            cur++
            val last = fields[cur]
            cur++

            val gender = try {
                FullName.Gender.valueOf(fields[cur])
            } catch (e: Exception) {
                FullName.Gender.ANY
            }
            cur++

            val portrait = fields[cur].ifBlank { null }
            cur++

            var tags: Set<String> = emptySet()

            if (metaVersionNumber < 2) {
                tags = fields[cur].takeIf { it.isNotBlank() }
                    ?.split(sep)
                    ?.toSet()
                    ?: emptySet()
                cur++
            }

            val personality = fields[cur]
            cur++

            val level = fields[cur].toInt()
            cur++

            val skills =
                fields[cur].takeIf { it.isNotBlank() }
                    ?.split(sep)
                    ?.mapNotNull {
                        val p = it.split(joinSep, limit = 2)
                        if (p.size == 2)
                            p[0] to p[1].toFloat()
                        else null
                    }?.toMap()
                    ?: emptyMap()
            cur++

            val rankId = fields[cur]
            cur++
            val postId = fields[cur]
            cur++

            val xp = fields[cur].toLong()
            cur++
            val bonusXp = fields[cur].toLong()
            cur++
            val points = fields[cur].toInt()
            cur++

            val metaMemKeys = if (metaVersionNumber == 2) {
                val tagsAndMemKeys = fields.getOrNull(cur)?.split(personSep + fieldSep)
                tags = tagsAndMemKeys?.getOrNull(0).takeIf { it?.isNotBlank() == true }
                    ?.split(memKeyJoinSep)
                    ?.toSet()
                    ?: emptySet()
                tagsAndMemKeys?.getOrNull(1)
            } else {
                fields.getOrNull(cur)
            }


            val memKeys =
                if (metaVersionNumber == 0) {
                    // DEPRECIATED
                    metaMemKeys
                        ?.takeIf { it.isNotBlank() }
                        ?.split(memKeySep)
                        ?.mapNotNull {
                            val p = it.split(memKeyJoinSep, limit = 2)
                            if (p.size == 2) {
                                val key = "$" + p[0]
                                val raw = p[1]

                                val value: Any =
                                    raw.toIntOrNull()
                                        ?: raw.toLongOrNull()
                                        ?: raw.toFloatOrNull()
                                        ?: raw.toDoubleOrNull()
                                        ?: raw.toShortOrNull()
                                        ?: raw.toByteOrNull()
                                        ?: raw.lowercase().toBooleanStrictOrNull()
                                        ?: raw

                                key to value
                            } else null
                        }
                        ?.toMap()
                        ?: emptyMap()
                } else {
                    metaMemKeys
                        ?.takeIf { it.isNotBlank() }
                        ?.split(memKeySep)
                        ?.mapNotNull {
                            val p = it.split(memKeyJoinSep, limit = 2)
                            if (p.size == 2) {
                                val key = "$" + p[0]
                                val raw = p[1]

                                val parsedValue = PrefixedCodec.decodeAny(raw)
                                if (!parsedValue.success) return@mapNotNull null

                                key to parsedValue.value
                            } else null
                        }
                        ?.toMap()
                        ?: emptyMap()
                }

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
            if (compress) "p2"
            else "P2"

        val ver = "$metaSep$structureVersion$metaSep"

        val parts = mutableListOf<String>()

        // Core identity
        parts += data.aiCoreId
        parts += data.first
        parts += data.last
        parts += data.gender.name
        parts += (data.portrait ?: "")

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

        parts += data.tags.joinToString(memKeyJoinSep) + personSep

        // Memory keys
        val memKeyString = data.memKeys.entries.mapNotNull { entry ->
            val key = entry.key
            val value = entry.value

            val formattedValue = PrefixedCodec.encode(value) ?: return@mapNotNull null

            "${key.removePrefix("$")}$memKeyJoinSep$formattedValue"
        }.joinToString(memKeySep)

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
            val readable = "${data.first}${if (data.last.isNotEmpty()) " " + data.last else ""} : $requiredMods"
            personString = readable + "\n" + personString
        }

        return personString
    }
}