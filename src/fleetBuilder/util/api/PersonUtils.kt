package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.FullName.Gender
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.PersonSettings
import org.magiclib.util.MagicLookup
import java.util.*


object PersonUtils {

    @JvmStatic
    fun getAllSourceModsFromPerson(
        person: PersonAPI,
        settings: PersonSettings = PersonSettings()
    ): Set<ModSpecAPI> {
        return getAllSourceModsFromPerson(DataPerson.getPersonDataFromPerson(person, settings))
    }

    @JvmStatic
    fun getAllSourceModsFromPerson(data: DataPerson.ParsedPersonData): Set<ModSpecAPI> {
        val sourceMods = mutableSetOf<ModSpecAPI>()

        for (skill in data.skills) {
            MagicLookup.getSkillSpec(skill.key)?.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        return sourceMods
    }

    /**
     * Copies the data from one PersonAPI instance to another. This includes name, portraitSprite,
     * level, xp, bonusXp, points, and skill levels.
     *
     * @param from the source PersonAPI instance
     * @param to the destination PersonAPI instance
     */
    @JvmStatic
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

    @JvmOverloads
    @JvmStatic
    fun getRandomPortrait(
        gender: FullName.Gender = FullName.Gender.ANY,
        factionID: String? = null,
        random: Random = Random()
    ): String {
        val settings = Global.getSettings()
        val faction = settings.getFactionSpec(factionID ?: Factions.PLAYER)

        return if (gender == Gender.MALE) {
            faction.malePortraits?.pick(random) ?: settings.getFactionSpec(Factions.PLAYER).malePortraits.pick(random)
        } else if (gender == Gender.FEMALE) {
            faction.femalePortraits.pick(random) ?: settings.getFactionSpec(Factions.PLAYER).femalePortraits.pick(random)
        } else {
            if (random.nextBoolean())
                faction.malePortraits.pick(random) ?: settings.getFactionSpec(Factions.PLAYER).malePortraits.pick(random)
            else
                faction.femalePortraits.pick(random) ?: settings.getFactionSpec(Factions.PLAYER).femalePortraits.pick(random)
        }
    }

    @JvmStatic
    fun randomizePersonCosmetics(
        officer: PersonAPI,
        faction: FactionAPI?
    ) {
        if (officer.isDefault || officer.isAICore)
            return

        val randomPerson = faction?.createRandomPerson()
        if (randomPerson != null) {
            officer.name = randomPerson.name
            officer.portraitSprite = randomPerson.portraitSprite
        } else {
            officer.name.gender = FullName.Gender.ANY
            officer.portraitSprite = PersonUtils.getRandomPortrait(officer.name.gender, factionID = faction?.id)
            officer.name.first = "Unknown"
        }
    }
}