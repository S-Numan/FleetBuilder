package fleetBuilder.temporary

import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.plugins.AutofitPlugin

class MLGenericAutofitDelegate(
    private val fleetMember: FleetMemberAPI,
    private val faction: FactionAPI,
) : AutofitPlugin.AutofitPluginDelegate {

    private val fighters: MutableList<AutofitPlugin.AvailableFighter> = ArrayList()
    private val weapons: MutableList<AutofitPlugin.AvailableWeapon> = ArrayList()
    private val hullmods = listOf(ArrayList<Any?>(faction.knownHullMods).toString())

    override fun fitFighterInSlot(index: Int, fighter: AutofitPlugin.AvailableFighter, variant: ShipVariantAPI) {
        variant.setWingId(index, fighter.id)//Set on variant
    }

    override fun clearFighterSlot(index: Int, variant: ShipVariantAPI) {
        variant.setWingId(index, null)
    }

    override fun fitWeaponInSlot(slot: WeaponSlotAPI, weapon: AutofitPlugin.AvailableWeapon, variant: ShipVariantAPI) {
        variant.addWeapon(slot.id, weapon.id)
    }

    override fun clearWeaponSlot(slot: WeaponSlotAPI, variant: ShipVariantAPI) {
        variant.clearSlot(slot.id)
    }

    override fun getAvailableWeapons(): List<AutofitPlugin.AvailableWeapon> {
        return weapons
    }

    fun addAvailableWeapon(weapon: AutofitPlugin.AvailableWeapon) {
        weapons.add(0, weapon)
    }

    override fun getAvailableFighters(): List<AutofitPlugin.AvailableFighter> {
        return fighters
    }

    fun addAvailableFighter(fighter: AutofitPlugin.AvailableFighter) {
        fighters.add(0, fighter)
    }

    override fun isPriority(weapon: WeaponSpecAPI): Boolean {
        return faction.isWeaponPriority(weapon.weaponId)
    }

    override fun isPriority(wing: FighterWingSpecAPI): Boolean {
        return faction.isFighterPriority(wing.id)
    }

    override fun getAvailableHullmods(): List<String> {
        return hullmods
    }

    override fun syncUIWithVariant(variant: ShipVariantAPI) {}
    override fun getShip(): ShipAPI? {
        //throw UnsupportedOperationException("Not implemented1")
        return null
    }

    override fun getFaction(): FactionAPI {
        return faction
    }

    override fun isAllowSlightRandomization(): Boolean = false
    override fun isPlayerCampaignRefit(): Boolean = false
    override fun canAddRemoveHullmodInPlayerCampaignRefit(modId: String): Boolean = true
    override fun getMarket(): MarketAPI? {
        return null
    }

    override fun getFleetMember(): FleetMemberAPI {
        return fleetMember
    }
}