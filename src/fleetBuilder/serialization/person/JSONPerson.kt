package fleetBuilder.serialization.person

import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBMisc
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.person.DataPerson.buildPersonFull
import fleetBuilder.serialization.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.util.lib.PrefixedCodec
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat
import java.util.*

object JSONPerson {
    @JvmOverloads
    fun extractPersonDataFromJson(
        json: JSONObject,
        missing: MissingContent = MissingContent()
    ): DataPerson.ParsedPersonData {
        val skills = mutableMapOf<String, Float>()
        json.optJSONObject("skills")?.let { skillsJson ->
            val keys = skillsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next() as? String ?: continue
                skills[key] = skillsJson.optFloat(key, 0f)
            }
        }

        val tags = buildSet {
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        }

        val memKeys = buildMap<String, Any?> {

            // DEPRECIATED
            json.optJSONObject("memKeys")?.let { memKeysJson ->
                memKeysJson.keys().forEach { keyName ->
                    if (keyName !is String) return@forEach

                    memKeysJson.opt(keyName)?.let { raw ->
                        if (raw !is String) return@forEach

                        val value =
                            raw.toIntOrNull()
                                ?: raw.toLongOrNull()
                                ?: raw.toFloatOrNull()
                                ?: raw.toDoubleOrNull()
                                ?: raw.toShortOrNull()
                                ?: raw.toByteOrNull()
                                ?: raw.lowercase().toBooleanStrictOrNull()
                                ?: raw

                        put("$$keyName", value)
                    }
                }
            }

            json.optJSONObject("memKeysV2")?.let { memKeysJson ->
                memKeysJson.keys().forEach { keyName ->
                    if (keyName !is String) return@forEach

                    memKeysJson.opt(keyName)?.let { raw ->
                        if (raw !is String) return@forEach

                        val parsedValue = PrefixedCodec.decodeAny(raw)
                        if (!parsedValue.success) return@forEach

                        put("$$keyName", parsedValue.value)
                    }
                }
            }

            // LEGACY behavior
            json.optJSONArray("trueMemKeys")?.let { arr ->
                for (i in 0 until arr.length()) {
                    put("$" + arr.optString(i), true)
                }
            }
            // LEGACY behavior
            if (json.optBoolean("mentored", false)) put("$" + Misc.MENTORED, true)
            if (json.optBoolean("mercenary", false)) put("$" + Misc.IS_MERCENARY, true)
            if (json.optBoolean("unremovable", false)) put("$" + Misc.CAPTAIN_UNREMOVABLE, true)
        }

        val gender = try {
            FullName.Gender.valueOf(json.optString("gender", "ANY"))
        } catch (_: Exception) {
            FullName.Gender.ANY
        }

        missing.gameMods.addAll(FBMisc.getModInfosFromJson(json))

        return DataPerson.ParsedPersonData(
            aiCoreId = json.optString("aicoreid", ""),
            first = json.optString("first", "Unknown"),
            last = json.optString("last", ""),
            gender = gender,
            portrait = json.optString("portrait", null),
            tags = tags,
            rankId = json.optString("rank", Ranks.SPACE_LIEUTENANT),
            postId = json.optString("post", Ranks.POST_OFFICER),
            personality = json.optString("personality", Personalities.STEADY),
            level = json.optInt("level", 0),
            skills = skills,
            xp = json.optLong("xp", 0),
            bonusXp = json.optLong("bonusxp", 0),
            points = json.optInt("points", 0),
            memKeys = memKeys
        )
    }


    @JvmOverloads
    fun getPersonFromJson(
        json: JSONObject,
        settings: PersonSettings = PersonSettings(),
        missing: MissingContent = MissingContent(),
        random: Random = Random()
    ): PersonAPI {
        val data = extractPersonDataFromJson(json, missing)

        return buildPersonFull(data, settings, missing, random)
    }

    @JvmOverloads
    fun savePersonToJson(person: PersonAPI, settings: PersonSettings = PersonSettings()): JSONObject {
        return savePersonToJson(getPersonDataFromPerson(person, settings))
    }

    fun savePersonToJson(data: DataPerson.ParsedPersonData): JSONObject {
        val json = JSONObject()

        if (data.aiCoreId.isNotBlank())
            json.put("aicoreid", data.aiCoreId)

        json.put("first", data.first)
        if (data.last.isNotBlank())
            json.put("last", data.last)
        if (data.gender != FullName.Gender.ANY)
            json.put("gender", data.gender)

        json.put("portrait", data.portrait)

        if (data.rankId != Ranks.SPACE_LIEUTENANT)
            json.put("rank", data.rankId)
        if (data.postId != Ranks.POST_OFFICER)
            json.put("post", data.postId)
        json.put("personality", data.personality)

        json.put("level", data.level)

        if (data.xp != 0L) {
            json.put("xp", data.xp)
        }
        if (data.bonusXp != 0L) {
            json.put("bonusxp", data.bonusXp)
        }
        if (data.points != 0) {
            json.put("points", data.points)
        }

        val personTags = buildList {
            data.tags.forEach { tag ->
                add(tag)
            }
        }

        if (personTags.isNotEmpty())
            json.put("tags", JSONArray(personTags))


        val memKeysJSON = JSONObject()

        data.memKeys.entries.forEach { entry ->
            val key = entry.key
            val value = entry.value

            val formattedValue = PrefixedCodec.encode(value) ?: return@forEach
            
            memKeysJSON.put(key.removePrefix("$"), formattedValue)

        }
        if (memKeysJSON.length() > 0)
            json.put("memKeysV2", memKeysJSON)

        val skillsObject = JSONObject()
        for (skill in data.skills) {
            skillsObject.put(skill.key, skill.value)
        }
        if (skillsObject.length() > 0)
            json.put("skills", skillsObject)

        return json
    }
}