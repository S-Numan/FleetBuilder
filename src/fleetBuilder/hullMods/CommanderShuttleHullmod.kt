package fleetBuilder.hullMods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.loading.HullModSpecAPI


class CommanderShuttleHullmod : BaseHullMod() {
    override fun init(spec: HullModSpecAPI?) {
        super.init(spec)
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String) {
        stats.dynamic.getStat(Stats.CORONA_EFFECT_MULT).baseValue = 0f
        stats.sensorProfile.baseValue = 0f
        stats.sensorStrength.baseValue = 0f
        stats.maxBurnLevel.baseValue = 15f
        stats.suppliesToRecover.baseValue = 0f

        stats.suppliesPerMonth.baseValue = 0f
        stats.fuelUseMod.modifyMult(id, 0f)

        //stats.fuelMod.modifyMult(id, 0f)
        //stats.cargoMod
        //stats.minCrewMod.modifyMult(id,0f)

        stats.baseCRRecoveryRatePercentPerDay.baseValue = 100f
        stats.repairRatePercentPerDay.baseValue = 100f

        stats.dynamic.getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyMult(id, 0f)
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {

    }

    override fun advanceInCombat(ship: ShipAPI?, amount: Float) {

        if (ship == null || !ship.isAlive || ship.isHulk) return

        val engine = Global.getCombatEngine()

        /*engine.applyDamage(
            ship,
            ship.location,
            ship.hitpoints * 10f,
            DamageType.HIGH_EXPLOSIVE,
            0f,
            true,
            false,
            ship
        )*/
        engine.removeEntity(ship)
    }

    override fun isBuiltIn(ship: ShipAPI?): Boolean {
        return true
    }

    override fun hasSModEffect(): Boolean {
        return false
    }

    override fun canBeAddedOrRemovedNow(
        ship: ShipAPI?,
        marketOrNull: MarketAPI?,
        mode: CampaignUIAPI.CoreUITradeMode?
    ): Boolean {
        return false
    }
}