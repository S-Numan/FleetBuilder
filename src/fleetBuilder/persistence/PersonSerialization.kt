package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.util.Misc
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.variants.MissingElements
import org.json.JSONArray
import org.json.JSONObject
import org.magiclib.kotlin.setMentored
import org.magiclib.kotlin.setMercenary
import org.magiclib.kotlin.setUnremovable
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
    /* <<<<<<<<<<  205ea2c6-66d2-4bef-a545-6f1936c458b9  >>>>>>>>>>> */

    @JvmOverloads
    fun getPersonFromJson(json: JSONObject, settings: PersonSettings = PersonSettings()): PersonAPI {
        return getPersonFromJsonWithMissing(json, settings).first
    }

    @JvmOverloads
    fun getPersonFromJsonWithMissing(
        json: JSONObject,
        settings: PersonSettings = PersonSettings()
    ): Pair<PersonAPI, MissingElements> {
        val missing = MissingElements()

        var person: PersonAPI? = null

        val aiCoreId = json.optString("aicoreid", "")
        if (aiCoreId.isNotEmpty()) {
            try {
                person = Misc.getAICoreOfficerPlugin(aiCoreId).createPerson(aiCoreId, Factions.PLAYER, Random())
                //AI's come with skills via getAiCoreOfficerPluginthis, we don't want this, so they will be removed.
                person.stats.skillsCopy.forEach { skill ->
                    person.stats.setSkillLevel(skill.skill.id, 0f)
                }
            } catch (_: Exception) {
            }
        }

        if (person == null) {
            person = Global.getSettings().createPerson()
        }

        // Safely handle name and gender
        person.name.first = json.optString("first", "Unknown")
        person.name.last = json.optString("last", "Officer")
        person.gender = try {
            FullName.Gender.valueOf(json.optString("gender", "MALE"))
        } catch (_: Exception) {
            if (Math.random() < 0.5)
                FullName.Gender.MALE
            else
                FullName.Gender.FEMALE
        }

        // Validate and set portrait if it exists
        val portrait = json.optString("portrait", "")
        try {
            if (portrait.isNotEmpty() && Global.getSettings().getSprite(portrait) != null)
                person.portraitSprite = portrait

        } catch (_: Exception) {
            val faction = Global.getSettings().getFactionSpec(Factions.PLAYER)
            val randomPortrait =
                if (person.gender == FullName.Gender.MALE)
                    faction.malePortraits.pick()
                else
                    faction.femalePortraits.pick()

            person.portraitSprite = randomPortrait
        }

        // Add tags if array exists
        if (json.has("tags")) {
            val tagsArray = json.optJSONArray("tags")
            for (i in 0 until (tagsArray?.length() ?: 0)) {
                person.addTag(tagsArray?.optString(i) ?: continue)
            }
        }

        person.rankId = json.optString("rank", Ranks.SPACE_LIEUTENANT)
        person.postId = json.optString("post", Ranks.POST_OFFICER)
        person.setPersonality(json.optString("personality", Personalities.STEADY))

        val personLevel = json.optInt("level", 0)//0 if unset
        person.stats.level = personLevel.coerceIn(1, 20)

        // Handle skills safely
        val skillsObject = json.optJSONObject("skills")
        if (skillsObject != null) {
            val keys = skillsObject.keys()
            while (keys.hasNext()) {
                val skillId = keys.next().toString()
                if (settings.excludeSkillsWithID.contains(skillId))
                    continue

                val level = skillsObject.optInt(skillId, 0).coerceIn(0, 2)
                if (level > 0) {
                    if (Global.getSettings().skillIds.contains(skillId)) {
                        person.stats.setSkillLevel(skillId, level.toFloat())
                    } else {
                        missing.skillIds.add(skillId)
                    }
                }
            }
        }

        if (personLevel == 0)//Person level was unset?
            person.stats.level = person.stats.skillsCopy.count { it.skill.isAptitudeEffect.not() && it.level > 0f } //Set it to the amount of skills, it's probably correct.


        if (settings.handleXpAndPoints) {
            person.stats.xp = json.optLong("xp", 0)
            person.stats.bonusXp = json.optLong("bonusxp", 0)
            person.stats.points = json.optInt("points", 0)
        }

        if (json.has("wasplayer"))
            person.addTag("wasplayer")

        if (json.has("trueMemKeys")) {
            val memKeysArray = json.optJSONArray("trueMemKeys")
            for (i in 0 until (memKeysArray?.length() ?: 0)) {
                person.memoryWithoutUpdate.set(memKeysArray?.optString(i) ?: continue, true)
            }
        }

        loadLegacyMentorMercenaryUnremovable(person, json)

        return Pair(person, missing)
    }

    private fun loadLegacyMentorMercenaryUnremovable(person: PersonAPI, json: JSONObject) {
        //Load JSON details saved from before version 1.4.1
        if (json.optBoolean("mentored", false)) {
            person.setMentored(true)
        }
        if (json.optBoolean("mercenary", false)) {
            person.setMercenary(true)
        }
        if (json.optBoolean("unremovable", false)) {
            person.setUnremovable(true)
        }
    }

    @JvmOverloads
    fun savePersonToJson(person: PersonAPI, settings: PersonSettings = PersonSettings()): JSONObject {
        val json = JSONObject()

        if (person.isAICore)
            json.put("aicoreid", person.aiCoreId)

        json.put("first", person.name.first)
        json.put("last", person.name.last)
        json.put("gender", person.gender.name)
        json.put("portrait", person.portraitSprite)
        json.put("tags", JSONArray(person.tags))
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
        if (person.isPlayer) {
            json.put("wasplayer", person.isPlayer)
        }

        val trueMemKeysJSON = JSONArray()

        person.memoryWithoutUpdate.keys.forEach { key ->
            val value = person.memoryWithoutUpdate.get(key)
            if (value is Boolean && value) {
                trueMemKeysJSON.put(key)
            }
        }
        if (trueMemKeysJSON.length() > 0)
            json.put("trueMemKeys", trueMemKeysJSON)

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