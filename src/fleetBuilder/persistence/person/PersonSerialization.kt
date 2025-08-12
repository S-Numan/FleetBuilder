package fleetBuilder.persistence.person

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings
import fleetBuilder.persistence.person.PersonSerialization.getPersonFromJson
import fleetBuilder.persistence.person.PersonSerialization.savePersonToJson
import fleetBuilder.util.FBMisc
import fleetBuilder.util.FBMisc.getRandomPortrait
import fleetBuilder.variants.GameModInfo
import fleetBuilder.variants.MissingElements
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

object PersonSerialization {

    /* <<<<<<<<<<<<<<  ✨ Windsurf Command 🌟 >>>>>>>>>>>>>>>> */
    /**
     * Holds settings for [savePersonToJson] and [getPersonFromJson].
     * @param handleXpAndPoints whether to save and load XP and skill points from the JSON. If false, these will be ignored.
     * @param excludeSkillsWithID a set of skill IDs to exclude when loading skills from the JSON. If a skill is in
     * this set, it will not be loaded.
     */
    data class PersonSettings(
        var handleXpAndPoints: Boolean = true,
        var excludeSkillsWithID: MutableSet<String> = mutableSetOf(),
    )

    data class ParsedPersonData(
        val aiCoreId: String = "",
        val first: String = "Unknown",
        val last: String,
        val gender: FullName.Gender,
        val portrait: String? = null,
        val tags: List<String> = emptyList(),
        val rank: String = Ranks.SPACE_LIEUTENANT,
        val post: String = Ranks.POST_OFFICER,
        val personality: String = Personalities.STEADY,
        val level: Int = 0,
        val skills: Map<String, Int> = emptyMap(),
        val xp: Long = 0,
        val bonusXp: Long = 0,
        val points: Int = 0,
        val memKeys: Map<String, Any> = emptyMap(),
        val gameMods: Set<GameModInfo>,
    )

    fun extractPersonDataFromJson(json: JSONObject): ParsedPersonData {
        val skills = mutableMapOf<String, Int>()
        json.optJSONObject("skills")?.let { skillsJson ->
            val keys = skillsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next() as? String ?: continue
                skills[key] = skillsJson.optInt(key, 0).coerceIn(0, 2)
            }
        }

        val tags = buildList {
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }

            //LEGACY
            if (json.has("wasplayer"))
                add("wasplayer")
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

        val gameMods = FBMisc.getModInfosFromJson(json)

        return ParsedPersonData(
            aiCoreId = json.optString("aicoreid", ""),
            first = json.optString("first", "Unknown"),
            last = json.optString("last", ""),
            gender = gender,
            portrait = json.optString("portrait", null),
            tags = tags,
            rank = json.optString("rank", Ranks.SPACE_LIEUTENANT),
            post = json.optString("post", Ranks.POST_OFFICER),
            personality = json.optString("personality", Personalities.STEADY),
            level = json.optInt("level", 0),
            skills = skills,
            xp = json.optLong("xp", 0),
            bonusXp = json.optLong("bonusxp", 0),
            points = json.optInt("points", 0),
            memKeys = memKeys,
            gameMods = gameMods
        )
    }

    fun filterParsedPersonData(data: ParsedPersonData, settings: PersonSettings): ParsedPersonData {
        return data.copy(
            skills = data.skills.filterKeys { it !in settings.excludeSkillsWithID },
            xp = if (settings.handleXpAndPoints) data.xp else 0,
            bonusXp = if (settings.handleXpAndPoints) data.bonusXp else 0,
            points = if (settings.handleXpAndPoints) data.points else 0
        )
    }

    fun validateAndCleanPersonData(data: ParsedPersonData, missing: MissingElements): ParsedPersonData {
        val validSkills = data.skills.filterKeys {
            val exists = Global.getSettings().skillIds.contains(it)
            if (!exists) missing.skillIds.add(it)
            exists
        }

        val validPortrait = try {
            if (data.portrait == null) {
                getRandomPortrait(data.gender)
            } else if (data.portrait.isNotEmpty()) {
                val sprite = Global.getSettings().getSprite(data.portrait)
                if (sprite == null || sprite.width == 0f) throw Exception()

                data.portrait
            } else data.portrait
        } catch (_: Exception) {
            getRandomPortrait(data.gender)
        }

        return data.copy(
            skills = validSkills,
            portrait = validPortrait
        )
    }

    fun buildPerson(data: ParsedPersonData): PersonAPI {
        val person = if (data.aiCoreId.isNotEmpty()) {
            try {
                Misc.getAICoreOfficerPlugin(data.aiCoreId).createPerson(data.aiCoreId, Factions.PLAYER, Random()).apply {
                    stats.skillsCopy.forEach { stats.setSkillLevel(it.skill.id, 0f) }
                }
            } catch (_: Exception) {
                Global.getSettings().createPerson()
            }
        } else {
            Global.getSettings().createPerson()
        }

        person.name.first = data.first
        person.name.last = data.last
        person.gender = data.gender
        person.rankId = data.rank
        person.postId = data.post
        person.setPersonality(data.personality)

        if (data.portrait == null) {
            person.portraitSprite = getRandomPortrait(data.gender)
        } else if (data.portrait.isNotEmpty()) {
            person.portraitSprite = data.portrait
        }

        data.tags.forEach { person.addTag(it) }
        data.skills.forEach { (id, level) -> person.stats.setSkillLevel(id, level.toFloat()) }

        val level = if (data.level > 0) data.level else data.skills.count()
        person.stats.level = level

        person.stats.xp = data.xp
        person.stats.bonusXp = data.bonusXp
        person.stats.points = data.points

        data.memKeys.forEach { (key, value) ->
            if (value is String || value is Boolean)
                person.memoryWithoutUpdate.set(key, value)
        }

        return person
    }

    fun buildPersonFull(
        rawData: ParsedPersonData,
        settings: PersonSettings = PersonSettings()
    ): Pair<PersonAPI, MissingElements> {
        val missing = MissingElements()

        // Filter data based on settings (e.g., exclude certain skills)
        val filteredData = filterParsedPersonData(rawData, settings)

        // Validate data (e.g., skills/portraits exist)
        val validatedData = validateAndCleanPersonData(filteredData, missing)

        // Build actual PersonAPI object
        val person = buildPerson(validatedData)

        return person to missing
    }

    @JvmOverloads
    fun getPersonFromJsonWithMissing(
        json: JSONObject,
        settings: PersonSettings = PersonSettings()
    ): Pair<PersonAPI, MissingElements> {
        val missing = MissingElements()

        // Extract raw data from JSON
        val rawData = extractPersonDataFromJson(json)
        missing.gameMods.addAll(rawData.gameMods)

        val (person, subMissing) = buildPersonFull(rawData, settings)
        missing.add(subMissing)

        return person to missing
    }

    @JvmOverloads
    fun getPersonFromJson(
        json: JSONObject,
        settings: PersonSettings = PersonSettings()
    ): PersonAPI {
        return getPersonFromJsonWithMissing(json, settings).first
    }


    @JvmOverloads
    fun savePersonToJson(person: PersonAPI, settings: PersonSettings = PersonSettings()): JSONObject {
        val json = JSONObject()

        if (person.isAICore)
            json.put("aicoreid", person.aiCoreId)

        json.put("first", person.name.first)
        if (person.name.last.isNotBlank())
            json.put("last", person.name.last)
        if (person.name.gender != FullName.Gender.ANY)
            json.put("gender", person.gender.name)

        json.put("portrait", person.portraitSprite)

        if (person.rankId != Ranks.SPACE_LIEUTENANT)
            json.put("rank", person.rankId)
        if (person.postId != Ranks.POST_OFFICER)
            json.put("post", person.postId)
        json.put("personality", person.personalityAPI.id)

        json.put("level", person.stats.level)

        if (settings.handleXpAndPoints) {
            if (person.stats.xp != 0L) {
                json.put("xp", person.stats.xp)
            }
            if (person.stats.bonusXp != 0L) {
                json.put("bonusxp", person.stats.bonusXp)
            }
            if (person.stats.points != 0) {
                json.put("points", person.stats.points)
            }
        }
        val personTags = buildList {
            person.tags.forEach { tag ->
                add(tag)
            }
            if (person.isPlayer) {
                add("wasplayer")
            }
        }

        if (personTags.isNotEmpty())
            json.put("tags", JSONArray(personTags))


        val memKeysJSON = JSONObject()

        val storedOfficer = person.memoryWithoutUpdate.keys.contains(ModSettings.storedOfficerTag)

        person.memoryWithoutUpdate.keys.forEach { key ->
            if (storedOfficer && key == Misc.CAPTAIN_UNREMOVABLE) return@forEach//Skip including captain unremovable if it was added just for storing the officer in storage.

            val value = person.memoryWithoutUpdate.get(key)
            if (value is Boolean
                || value is String
            ) {
                memKeysJSON.put(key.removePrefix("$"), value)
            }
        }
        if (memKeysJSON.length() > 0)
            json.put("memKeys", memKeysJSON)

        val skillsObject = JSONObject()
        for (skill in person.stats.skillsCopy) {
            if (skill.skill.isAptitudeEffect || skill.level <= 0f || settings.excludeSkillsWithID.contains(skill.skill.id)) continue
            skillsObject.put(skill.skill.id, skill.level.toInt())
        }
        if (skillsObject.length() > 0)
            json.put("skills", skillsObject)


        return json
    }
}