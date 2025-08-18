package fleetBuilder.persistence.person

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings
import fleetBuilder.persistence.person.DataPerson.buildPersonFull
import fleetBuilder.persistence.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.util.FBMisc
import fleetBuilder.variants.MissingElements
import org.histidine.chatter.ChatterDataManager
import org.json.JSONArray
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.optFloat

object JSONPerson {
    @JvmOverloads
    fun extractPersonDataFromJson(
        json: JSONObject,
        missing: MissingElements = MissingElements()
    ): DataPerson.ParsedPersonData {
        val skills = mutableMapOf<String, Float>()
        json.optJSONObject("skills")?.let { skillsJson ->
            val keys = skillsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next() as? String ?: continue
                skills[key] = skillsJson.optFloat(key, 0f).coerceIn(0f, 2f)
            }
        }

        val tags = buildSet {
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        }

        val memKeys = buildMap<String, Any> {
            json.optJSONObject("memKeys")?.let { memKeysJson ->
                memKeysJson.keys().forEach { keyName ->
                    if (keyName !is String) return@forEach
                    memKeysJson.opt(keyName)?.let { obj ->
                        put("$$keyName", obj)
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

        val chatterID = json.optString("chatterID", null)
        val combatChatterData = if (chatterID != null) {
            CombatChatterSerialization.CombatChatterData(chatterID = chatterID)
        } else null

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
            memKeys = memKeys,
            combatChatterData = combatChatterData,
        )
    }


    @JvmOverloads
    fun getPersonFromJson(
        json: JSONObject,
        settings: PersonSettings = PersonSettings(),
        missing: MissingElements = MissingElements()
    ): PersonAPI {
        val data = extractPersonDataFromJson(json, missing)

        return buildPersonFull(data, settings, missing)
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

        val storedOfficer = data.memKeys.keys.contains(ModSettings.storedOfficerTag)

        data.memKeys.keys.forEach { key ->
            if (storedOfficer && (key == Misc.CAPTAIN_UNREMOVABLE || key == ModSettings.storedOfficerTag)) return@forEach//Skip including captain unremovable if it was added just for storing the officer in storage.

            val value = data.memKeys[key]
            memKeysJSON.put(key.removePrefix("$"), value)
        }
        if (memKeysJSON.length() > 0)
            json.put("memKeys", memKeysJSON)

        val skillsObject = JSONObject()
        for (skill in data.skills) {
            skillsObject.put(skill.key, skill.value.toInt())
        }
        if (skillsObject.length() > 0)
            json.put("skills", skillsObject)

        if (Global.getSettings().modManager.isModEnabled("chatter")) {
            val characterId = data.memKeys[ChatterDataManager.CHARACTER_MEMORY_KEY] as? String // TODO, this is a memory key right? isn't this saved normally then? and thus can be removed?

            if (characterId != null) {
                //val character = ChatterDataManager.getCharacterData(characterId)
                json.put("chatterID", characterId)
            }
        }


        return json
    }
}