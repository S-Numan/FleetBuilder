package fleetBuilder.util.api

import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.Misc
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.member.MemberSettings
import java.util.*

object MemberUtils {

    fun getAllSourceModsFromMember(
        member: FleetMemberAPI,
        settings: MemberSettings = MemberSettings()
    ): Set<ModSpecAPI> {
        return getAllSourceModsFromMember(DataMember.getMemberDataFromMember(member, settings))
    }

    fun getAllSourceModsFromMember(data: DataMember.ParsedMemberData): Set<ModSpecAPI> {
        val sourceMods = mutableSetOf<ModSpecAPI>()

        if (data.variantData != null)
            sourceMods.addAll(VariantUtils.getAllSourceModsFromVariant(data.variantData))
        if (data.personData != null)
            sourceMods.addAll(PersonUtils.getAllSourceModsFromPerson(data.personData))

        return sourceMods
    }

    fun randomizeMemberCosmetics(
        member: FleetMemberAPI,
        fleet: FleetDataAPI
    ) {
        member.shipName = fleet.pickShipName(member, Random())
        PersonUtils.randomizePersonCosmetics(member.captain, fleet.fleet?.faction)
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
}