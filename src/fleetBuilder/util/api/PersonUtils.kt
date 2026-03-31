package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.PersonSettings
import fleetBuilder.util.LookupUtils
import java.util.*


object PersonUtils {

    fun getMaxOfficerLevel(person: PersonAPI): Int {
        if (person.isPlayer) {
            val levelUpPlugin = Global.getSettings().levelupPlugin
            return levelUpPlugin.maxLevel
            //return Global.getSettings().getInt("playerMaxLevel")
        } else if (!person.isAICore) {
            val plugin = Global.getSettings().getPlugin("officerLevelUp") as OfficerLevelupPlugin
            return plugin.getMaxLevel(person)
        }
        return person.stats.level
    }

    fun getMaxOfficerEliteSkills(person: PersonAPI): Int {
        if (!person.isAICore) {
            val plugin = Global.getSettings().getPlugin("officerLevelUp") as OfficerLevelupPlugin
            return plugin.getMaxEliteSkills(person)
        }
        return person.stats.level
    }

    fun getAllSourceModsFromPerson(
        person: PersonAPI,
        settings: PersonSettings = PersonSettings()
    ): Set<ModSpecAPI> {
        return getAllSourceModsFromPerson(DataPerson.getPersonDataFromPerson(person, settings))
    }

    fun getAllSourceModsFromPerson(data: DataPerson.ParsedPersonData): Set<ModSpecAPI> {
        val sourceMods = mutableSetOf<ModSpecAPI>()

        for (skill in data.skills) {
            LookupUtils.getSkillSpec(skill.key)?.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        return sourceMods
    }

    fun copyOfficerDataTo(from: PersonAPI, to: PersonAPI) {
        //to.id = from.id
        to.name = from.name
        to.portraitSprite = from.portraitSprite

        val fromStats = from.stats
        val toStats = to.stats
        if (fromStats != null && toStats != null) {
            toStats.level = fromStats.level

            toStats.xp = fromStats.xp
            toStats.bonusXp = fromStats.bonusXp
            toStats.points = fromStats.points

            toStats.skillsCopy.forEach { skill ->
                toStats.setSkillLevel(skill.skill.id, 0f)
            }

            fromStats.skillsCopy.forEach { skill ->
                toStats.setSkillLevel(skill.skill.id, skill.level)
            }
        }
    }

    fun getRandomPortrait(
        gender: FullName.Gender = FullName.Gender.ANY,
        faction: String? = null,
        random: Random = Random()
    ): String {
        val faction = Global.getSettings().getFactionSpec(faction ?: Factions.PLAYER)
        return if (gender == FullName.Gender.MALE)
            faction.malePortraits.pick(random)
        else if (gender == FullName.Gender.FEMALE)
            faction.femalePortraits.pick(random)
        else
            if (random.nextBoolean()) faction.malePortraits.pick(random) else faction.femalePortraits.pick(random)
    }

    fun randomizePersonCosmetics(
        officer: PersonAPI,
        faction: FactionAPI?
    ) {
        if (!officer.isDefault && !officer.isAICore) {
            val randomPerson = faction?.createRandomPerson()
            if (randomPerson != null) {
                officer.name = randomPerson.name
                officer.portraitSprite = randomPerson.portraitSprite
            } else {
                officer.name.gender = FullName.Gender.ANY
                officer.portraitSprite = PersonUtils.getRandomPortrait(officer.name.gender, faction = faction?.id)
                officer.name.first = "Unknown"
            }
        }
    }
}