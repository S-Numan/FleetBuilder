package fleetBuilder.persistence

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import org.json.JSONArray
import org.json.JSONObject
import org.magiclib.kotlin.isMentored
import org.magiclib.kotlin.isMercenary
import org.magiclib.kotlin.setMentored
import org.magiclib.kotlin.setMercenary

object OfficerSerialization {

    fun getOfficerFromJson(json: JSONObject): PersonAPI {
        val person = Global.getFactory().createPerson()
        val aiCoreId = json.optString("aicoreid", "")

        if (aiCoreId.isNotEmpty())
            person.aiCoreId = aiCoreId

        // Safely handle name and gender
        person.name.first = json.optString("first", "Unknown")
        person.name.last = json.optString("last", "Officer")
        person.gender = try {
            FullName.Gender.valueOf(json.optString("gender", "MALE"))
        } catch (e: Exception) {
            FullName.Gender.MALE // fallback
        }

        // Validate and set portrait if it exists
        val portrait = json.optString("portrait", "")
        try {
            if (portrait.isNotEmpty() && Global.getSettings().getSprite(portrait) != null) {
                person.portraitSprite = portrait
            }
        } catch (_: Exception) {
            // Silently skip invalid portrait
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

        if (json.optBoolean("mentored", false)) {
            person.setMentored(true)
        }

        if (json.optBoolean("mercenary", false)) {
            person.setMercenary(true)
        }

        if (json.optBoolean("unremovable", false)) {
            person.memory.set("\$captain_unremovable", true)
        }

        // Handle skills safely
        val skillsObject = json.optJSONObject("skills")
        if (skillsObject != null) {
            val keys = skillsObject.keys()
            while (keys.hasNext()) {
                val skillId = keys.next().toString()
                val level = skillsObject.optInt(skillId, 0).coerceIn(0, 2)
                if (level > 0 && Global.getSettings().skillIds.contains(skillId)) {
                    person.stats.setSkillLevel(skillId, level.toFloat())
                }
            }
        }

        if (personLevel == 0)//Person level was unset?
            person.stats.level = person.stats.skillsCopy.size//Set it to the amount of skills, it's probably correct.


        person.stats.xp = json.optLong("xp", 0)
        person.stats.bonusXp = json.optLong("bonusxp", 0)
        person.stats.points = json.optInt("points", 0)

        if (json.has("wasplayer"))
            person.addTag("wasplayer")

        return person
    }

    @JvmOverloads
    fun saveOfficerToJson(person: PersonAPI, storeLevelingStats: Boolean = true): JSONObject {
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

        if (storeLevelingStats) {
            json.put("level", person.stats.level)

            if (person.isMentored()) {
                json.put("mentored", person.isMentored())
            }
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
        if (person.isMercenary()) {
            json.put("mercenary", person.isMercenary())
        }
        if (person.isPlayer) {
            json.put("wasplayer", person.isPlayer)
        }
        if (person.memoryWithoutUpdate.getBoolean("\$captain_unremovable")) {
            json.put("unremovable", true)
        }

        val skillsObject = JSONObject()
        for (skill in person.stats.skillsCopy) {
            if (skill.skill.isAptitudeEffect || skill.level <= 0f) continue
            skillsObject.put(skill.skill.id, skill.level.toInt())
        }
        json.put("skills", skillsObject)

        return json
    }
}