package fleetBuilder.persistence.person

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.util.Misc
import fleetBuilder.util.FBMisc.getRandomPortrait
import fleetBuilder.variants.MissingElements
import org.histidine.chatter.ChatterDataManager
import org.histidine.chatter.combat.ChatterCombatPlugin
import java.util.Random

object DataPerson {
    data class ParsedPersonData(
        val aiCoreId: String = "",
        val first: String = "Unknown",
        val last: String = "",
        val gender: FullName.Gender = FullName.Gender.ANY,
        val portrait: String? = null,
        val tags: Set<String> = emptySet(),
        val rankId: String = Ranks.SPACE_LIEUTENANT,
        val postId: String = Ranks.POST_OFFICER,
        val personality: String = Personalities.STEADY,
        val level: Int = 0,
        val skills: Map<String, Float> = emptyMap(),
        val xp: Long = 0,
        val bonusXp: Long = 0,
        val points: Int = 0,
        val memKeys: Map<String, Any> = emptyMap()
    )

    @JvmOverloads
    fun copyPerson(
        person: PersonAPI,
        settings: PersonSettings = PersonSettings()
    ): PersonAPI {
        val data = getPersonDataFromPerson(person, settings)
        return buildPerson(data)
    }

    @JvmOverloads
    fun getPersonDataFromPerson(
        person: PersonAPI, settings: PersonSettings = PersonSettings(),
        filterParsed: Boolean = true
    ): ParsedPersonData {
        val data = ParsedPersonData(
            aiCoreId = if (person.isAICore) person.aiCoreId else "",
            first = person.name.first,
            last = person.name.last,
            gender = person.name.gender,
            portrait = person.portraitSprite,
            tags = person.tags,
            rankId = person.rankId ?: Ranks.SPACE_LIEUTENANT,
            postId = person.postId ?: Ranks.POST_OFFICER,
            personality = person.personalityAPI.id,
            level = person.stats.level,
            skills = person.stats.skillsCopy.associate { it.skill.id to it.level },
            xp = person.stats.xp,
            bonusXp = person.stats.bonusXp,
            points = person.stats.points,
            memKeys = person.memoryWithoutUpdate.keys.associateWith { key -> person.memoryWithoutUpdate[key] }
        )
        if (filterParsed)
            return filterParsedPersonData(data, settings)
        else
            return data
    }

    @JvmOverloads
    fun filterParsedPersonData(
        data: ParsedPersonData,
        settings: PersonSettings = PersonSettings(),
        missing: MissingElements = MissingElements()
    ): ParsedPersonData {

        // Lambda to decide if a skill should be kept
        val shouldKeepSkill: (String) -> Boolean = { skillId ->
            val skillSpec = Global.getSettings().skillIds
                .firstOrNull { it == skillId }
                ?.let { Global.getSettings().getSkillSpec(it) }

            skillSpec != null &&
                    skillId !in settings.excludeSkillsWithID &&
                    !skillSpec.isAptitudeEffect &&
                    (data.skills[skillId] ?: 0f) > 0f
        }

        // Remove filtered-out skills from missing
        missing.skillIds.retainAll { shouldKeepSkill(it) }

        val excludeKeys = setOf(
            "\$autoPointsMult"
        )

        val peopleKeys = setOf(
            "\$Post", "\$Rank", "\$id", "\$importance", "\$importanceAtLeastHigh",
            "\$importanceAtMostLow", "\$isContact", "\$isPerson", "\$level",
            "\$menuState", "\$name", "\$option", "\$personName", "\$personality",
            "\$post", "\$postAOrAn", "\$postId", "\$rank", "\$rankAOrAn",
            "\$rankId", "\$relName", "\$rel", "\$relValue"
        )

        // Filter memory keys
        val filteredMemory = data.memKeys.filterKeys { key ->
            val value = data.memKeys[key]

            when {
                value !is Boolean && value !is String && value !is Int -> return@filterKeys false
                key in excludeKeys -> return@filterKeys false
                settings.excludePeopleMemoryKeys && key in peopleKeys -> return@filterKeys false
                else -> return@filterKeys true
            }
        }

        return data.copy(
            skills = data.skills.filterKeys(shouldKeepSkill),
            memKeys = filteredMemory,
            xp = if (settings.handleXpAndPoints) data.xp else 0,
            bonusXp = if (settings.handleXpAndPoints) data.bonusXp else 0,
            points = if (settings.handleXpAndPoints) data.points else 0
        )
    }


    @JvmOverloads
    fun validateAndCleanPersonData(
        data: ParsedPersonData,
        missing: MissingElements = MissingElements()
    ): ParsedPersonData {
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
        person.rankId = data.rankId
        person.postId = data.postId
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
            if (value is String || value is Boolean || value is Int)
                person.memoryWithoutUpdate.set(key, value)
        }

        if (Global.getSettings().modManager.isModEnabled("chatter") && data.memKeys.containsKey("chatterChar")) {
            val charID = data.memKeys["chatterChar"] as? String
            if (charID != null) {
                val character = ChatterDataManager.getCharacterData(charID)
                if (character != null) {
                    ChatterDataManager.saveCharacter(person, charID)
                    val plugin = ChatterCombatPlugin.getInstance()
                    plugin?.setCharacterForOfficer(person, charID)
                }
            }
        }

        return person
    }

    @JvmOverloads
    fun buildPersonFull(
        data: ParsedPersonData,
        settings: PersonSettings = PersonSettings(),
        missing: MissingElements = MissingElements()
    ): PersonAPI {
        val ourMissing = MissingElements()

        // Validate data (e.g., skills/portraits exist)
        val validatedData = validateAndCleanPersonData(data, ourMissing)

        // Filter data based on settings (e.g., exclude certain skills)
        val filteredData = filterParsedPersonData(validatedData, settings, ourMissing)
        missing.add(ourMissing)

        // Build actual PersonAPI object
        return buildPerson(filteredData)
    }
}