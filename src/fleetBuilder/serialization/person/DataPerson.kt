package fleetBuilder.serialization.person

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.impl.campaign.ids.Ranks
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBConst
import fleetBuilder.serialization.MissingContent
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.api.PersonUtils
import org.histidine.chatter.ChatterDataManager
import org.histidine.chatter.combat.ChatterCombatPlugin
import java.util.*

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
        val level: Int = -1,
        val skills: Map<String, Float> = emptyMap(),
        val xp: Long = 0,
        val bonusXp: Long = 0,
        val points: Int = 0,
        val memKeys: Map<String, Any> = emptyMap()
    )

    @JvmOverloads
    fun clonePerson(
        person: PersonAPI,
        filterParsed: Boolean = false
    ): PersonAPI {
        val data = getPersonDataFromPerson(person, filterParsed = filterParsed)
        return buildPerson(data)
    }

    fun clonePerson(
        person: PersonAPI,
        settings: PersonSettings
    ): PersonAPI {
        val data = getPersonDataFromPerson(person, settings)
        return buildPerson(data)
    }

    @JvmOverloads
    fun getPersonDataFromPerson(
        person: PersonAPI, settings: PersonSettings = PersonSettings(),
        filterParsed: Boolean = true
    ): ParsedPersonData {
        val memKeys = person.memoryWithoutUpdate.keys.associateWith { key -> person.memoryWithoutUpdate[key] }.toMutableMap()

        // Vanilla AICore officers in enemy fleets are not marked as built in even if they exceed the usual max level and thus should be considered as so. We fix that here.
        if (person.isAICore) {
            val sameTypeAICore = runCatching {
                Misc.getAICoreOfficerPlugin(person.aiCoreId).createPerson(person.aiCoreId, Factions.PLAYER, Random()).apply {
                    stats.skillsCopy.forEach { stats.setSkillLevel(it.skill.id, 0f) }
                }
            }.getOrNull()
            if (sameTypeAICore != null && sameTypeAICore.stats.level < person.stats.level) // If the same type AI core has a lower level
                memKeys[Misc.CAPTAIN_UNREMOVABLE] = true // Then this is a built-in AI core that has had it's level +1'ed
        }

        if (memKeys.containsKey(FBConst.STORED_OFFICER_TAG)) {
            memKeys.remove(FBConst.STORED_OFFICER_TAG)
            memKeys.remove(Misc.CAPTAIN_UNREMOVABLE)
        }

        val data = ParsedPersonData(
            aiCoreId = if (person.isAICore) person.aiCoreId else "",
            first = person.name.first,
            last = person.name.last,
            gender = person.name.gender,
            portrait = person.portraitSprite,
            tags = person.tags.toSet(),
            rankId = person.rankId ?: Ranks.SPACE_LIEUTENANT,
            postId = person.postId ?: Ranks.POST_OFFICER,
            personality = person.personalityAPI.id,
            level = person.stats.level,
            skills = person.stats.skillsCopy.associate { it.skill.id to it.level },
            xp = person.stats.xp,
            bonusXp = person.stats.bonusXp,
            points = person.stats.points,
            memKeys = memKeys
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
        missing: MissingContent = MissingContent()
    ): ParsedPersonData {
        fun shouldKeepSkill(skillId: String): Boolean {
            if (skillId in settings.excludeSkillsWithID) return false
            if ((data.skills[skillId] ?: 0f) <= 0f) return false

            val skillSpec = LookupUtils.getSkillSpec(skillId)
            if (skillSpec != null) {
                if (skillSpec.hasTag(FBConst.NO_COPY_TAG)) return false
                if (skillSpec.isAptitudeEffect) return false
            }
            return true
        }

        fun shouldKeepTag(tagId: String): Boolean {
            return tagId !in settings.excludeTagsWithID && !tagId.startsWith("#")
        }

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
        val filteredMemory = if (!settings.includeMemKeys) emptyMap() else
            data.memKeys.filterKeys { key ->
                if (!settings.includeMemKeys) return@filterKeys false
                if (key.startsWith("$#")) return@filterKeys false

                val value = data.memKeys[key]

                when {
                    value !is Boolean && value !is String && value !is Int && value !is Float && value !is Double && value !is Long && value !is Short && value !is Byte && value !is Char -> return@filterKeys false
                    key in excludeKeys -> return@filterKeys false
                    settings.excludePeopleMemoryKeys && key in peopleKeys -> return@filterKeys false
                    else -> return@filterKeys true
                }
            }


        val newSkills = if (!settings.includeSkills) {
            missing.skillIds.clear()

            emptyMap()
        } else {
            missing.skillIds.retainAll { shouldKeepSkill(it) }
            // Remove filtered-out skills from missing, as they aren't missing if they are filtered out

            data.skills.filter { shouldKeepSkill(it.key) }
        }

        val filteredTags = if (!settings.includeTags) emptySet() else
            data.tags.filter(::shouldKeepTag)

        return data.copy(
            skills = newSkills,
            memKeys = filteredMemory,
            xp = if (settings.handleXpAndPoints) data.xp else 0,
            bonusXp = if (settings.handleXpAndPoints) data.bonusXp else 0,
            points = if (settings.handleXpAndPoints) data.points else 0,
            rankId = if (settings.handleRankAndPost) data.rankId else Ranks.SPACE_LIEUTENANT,
            postId = if (settings.handleRankAndPost) data.postId else Ranks.POST_OFFICER,
            tags = filteredTags.toSet()
        )
    }


    @JvmOverloads
    fun validateAndCleanPersonData(
        data: ParsedPersonData,
        missing: MissingContent = MissingContent(),
        random: Random = Random()
    ): ParsedPersonData {
        val validSkills = data.skills.filterKeys {
            val exists = Global.getSettings().skillIds.contains(it)
            if (!exists) missing.skillIds.add(it)
            exists
        }

        val validPortrait = try {
            if (data.portrait == null) {
                PersonUtils.getRandomPortrait(data.gender, random = random)
            } else if (data.portrait.isNotEmpty()) {
                val sprite = Global.getSettings().getSprite(data.portrait)
                if (sprite == null || sprite.width == 0f) throw Exception()

                data.portrait
            } else data.portrait
        } catch (_: Exception) {
            PersonUtils.getRandomPortrait(data.gender, random = random)
        }

        return data.copy(
            skills = validSkills,
            portrait = validPortrait
        )
    }

    fun buildPerson(data: ParsedPersonData, random: Random = Random()): PersonAPI {
        val person = if (data.aiCoreId.isNotEmpty()) {
            try {
                Misc.getAICoreOfficerPlugin(data.aiCoreId).createPerson(data.aiCoreId, Factions.PLAYER, random).apply {
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
            person.portraitSprite = PersonUtils.getRandomPortrait(data.gender, random = random)
        } else if (data.portrait.isNotEmpty()) {
            person.portraitSprite = data.portrait
        }

        data.tags.forEach { person.addTag(it) }
        data.skills.forEach { (id, level) -> person.stats.setSkillLevel(id, level) }

        val level = if (data.level > -1) data.level else data.skills.count { it.value >= 1f }
        person.stats.level = level

        person.stats.xp = data.xp
        person.stats.bonusXp = data.bonusXp
        person.stats.points = data.points

        data.memKeys.forEach { (key, value) ->
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
        missing: MissingContent = MissingContent(),
        random: Random = Random()
    ): PersonAPI {
        val ourMissing = MissingContent()

        // Validate data (e.g., skills/portraits exist)
        val validatedData = validateAndCleanPersonData(data, ourMissing, random)

        // Filter data based on settings (e.g., exclude certain skills)
        val filteredData = filterParsedPersonData(validatedData, settings, ourMissing)
        missing.add(ourMissing)

        // Build actual PersonAPI object
        return buildPerson(filteredData, random)
    }
}