package fleetBuilder.util.api

import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.Misc
import java.util.*

object MemberUtils {
    fun randomizeMemberCosmetics(
        member: FleetMemberAPI,
        fleet: FleetDataAPI
    ) {
        member.shipName = fleet.pickShipName(member, Random())
        randomizePersonCosmetics(member.captain, fleet.fleet?.faction)
    }


    fun getMaxSMods(fleetMember: FleetMemberAPI): Int {
        return getMaxSMods(fleetMember.stats)
    }

    fun getMaxSMods(stats: MutableShipStatsAPI): Int {
        return stats.dynamic
            .getMod(Stats.MAX_PERMANENT_HULLMODS_MOD)
            .computeEffective(Misc.MAX_PERMA_MODS.toFloat()).toInt()
        //stats.dynamic.getStat(Stats.MAX_PERMANENT_HULLMODS_MOD).modifiedInt
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