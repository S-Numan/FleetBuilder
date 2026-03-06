package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import java.util.*

object PersonUtils {
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

    fun getRandomPortrait(gender: FullName.Gender = FullName.Gender.ANY, faction: String? = null): String {
        val faction = Global.getSettings().getFactionSpec(faction ?: Factions.PLAYER)
        return if (gender == FullName.Gender.MALE)
            faction.malePortraits.pick()
        else if (gender == FullName.Gender.FEMALE)
            faction.femalePortraits.pick()
        else
            if (Random().nextBoolean()) faction.malePortraits.pick() else faction.femalePortraits.pick()
    }
}