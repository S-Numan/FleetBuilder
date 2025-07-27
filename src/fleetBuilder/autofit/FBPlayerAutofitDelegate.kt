package fleetBuilder.autofit

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSlotAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.plugins.AutofitPlugin
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.econ.Submarket
import com.fs.starfarer.coreui.refit.FighterPickerDialog
import com.fs.starfarer.coreui.refit.WeaponPickerDialog
import com.fs.starfarer.loading.specs.BaseWeaponSpec
import com.fs.starfarer.loading.specs.FighterWingSpec
import com.fs.starfarer.loading.specs.HullVariantSpec
import starficz.ReflectionUtils.invoke

class FBPlayerAutofitDelegate(
    private val fleetMember: FleetMemberAPI,
    private val faction: FactionAPI,
    private val ship: ShipAPI,
    private val coreUI: CoreUIAPI,
    private val shipDisplay: UIPanelAPI
) : AutofitPlugin.AutofitPluginDelegate {

    private val fighters: MutableList<FighterPickerDialog.o> = ArrayList()
    private val weapons: MutableList<WeaponPickerDialog.o> = ArrayList()
    private val hullmods: MutableList<String> = if (Global.getSettings().isInCampaignState) {
        faction.knownHullMods.toMutableList()
    } else {
        Global.getSettings().allHullModSpecs
            .map { it.id }
            .toMutableList()
    }

    //When null, source is assumed to be from player cargo
    private var market: MarketAPI? = null

    /*private val fighterSlotSources: MutableMap<Int, SubmarketAPI?> = mutableMapOf()
    private val weaponSlotSources: MutableMap<String, SubmarketAPI?> = mutableMapOf()
*/
    fun setMarket(_market: MarketAPI) {
        market = _market
        //fighterSlotSources.clear()
        //weaponSlotSources.clear()
    }

    override fun fitFighterInSlot(index: Int, fighter: AutofitPlugin.AvailableFighter, variant: ShipVariantAPI) {
        /*if(market != null && fighter.submarket != null) {//Came from a submarket
            fighterSlotSources[index] = fighter.submarket//Set source submarket for this slot
            playerCargo.credits.subtract(fighter.price)//Deduct credits
        } else {//Did not come from a submarket. This most likely came from the player's cargo
            fighterSlotSources[index] = null//Set source submarket to be null, as it didn't come from one.
        }

        fighter.quantity -= 1//Remove 1 from cargo
        variant.setWingId(index, fighter.id)//Set on variant
        */


        //val slottedWeapons = ReflectionUtils.invoke(("getSlottedWeapons"), shipDisplay) as? Map<String, WeaponPickerDialog.o>
        //val slottedFighters = ReflectionUtils.invoke(("getSlottedFighters"), shipDisplay) as? Map<String, FighterPickerDialog.o>
        //if(slottedWeapons.size != 0) {
        //    val apple = slottedWeapons.values.elementAt(0)
        //}

        //val wing = FighterPickerDialog.o(oFighter.quantity, oFighter.source, oFighter.wingSpec as FighterWingSpec, oFighter.id, oFighter.submarket as Submarket, coreUI)

        val oFighter = fighters.find { it.id == fighter.id }
        shipDisplay.invoke(
            "insertInFighterSlot", index//Fighter bay slot
            , oFighter, false//Boolean: Clear Slot
            , variant as HullVariantSpec
        )

        if (oFighter != null) {
            if (oFighter.source.getNumFighters(oFighter.id) == 0) {
                fighters.remove(oFighter)
            }
        }
        //if(oFighter != null) {
        //oFighter.quantity--
        //}
        //val slottedFighters = ReflectionUtils.invoke("getSlottedFighters", shipDisplay) as MutableMap<String, FighterPickerDialog.o?>
        //slottedFighters.put("${variant.getHullVariantId()}_$index", oFighter)

    }

    override fun clearFighterSlot(index: Int, variant: ShipVariantAPI) {
        /*val spec = variant.getWing(index)
        if(spec == null) return

        var foundSource = false
        if(market != null) {
            val sourceSubMarket = fighterSlotSources[index]
            if(sourceSubMarket != null) {
                playerCargo.credits.add(spec.baseValue * (1 + sourceSubMarket.tariff))
                sourceSubMarket.cargo.addFighters(spec.id, 1)
                foundSource = true
            }
        }
        if(!foundSource) {
            playerCargo.addFighters(spec.id, 1)
        }

        fighterSlotSources[index] = null
        variant.setWingId(index, null)
        */


        shipDisplay.invoke(
            "clearFighterSlot", index//Fighter slot
            , variant as HullVariantSpec
        )
    }

    override fun fitWeaponInSlot(slot: WeaponSlotAPI, weapon: AutofitPlugin.AvailableWeapon, variant: ShipVariantAPI) {
        /*
        if(market != null && weapon.submarket != null) {//Came from a submarket
            weaponSlotSources[slot.id] = weapon.submarket
            playerCargo.credits.subtract(weapon.price)
        } else {
            weaponSlotSources[slot.id] = null
        }

        weapon.quantity -= 1
        variant.addWeapon(slot.id, weapon.id)*/

        val oWeapon = weapons.find { it.id == weapon.id }
        shipDisplay.invoke(
            "insertInSlot", slot//Weapon bay slot
            , oWeapon, false//Clear slot
            , variant as HullVariantSpec
        )
        if (oWeapon != null) {
            if (oWeapon.source.getNumWeapons(oWeapon.id) == 0) {
                weapons.remove(oWeapon)
            }
        }
        /*if(oWeapon != null) {
            oWeapon.quantity--
        }*/
    }

    override fun clearWeaponSlot(slot: WeaponSlotAPI, variant: ShipVariantAPI) {
        /*val spec = variant.getWeaponSpec(slot.id)
        if(spec == null) return

        var foundSource = false
        if(market != null) {
            val sourceSubMarket = weaponSlotSources[slot.id]
            if(sourceSubMarket != null) {
                playerCargo.credits.add(spec.baseValue * (1 + sourceSubMarket.tariff))
                sourceSubMarket.cargo.addWeapons(spec.weaponId, 1)
                foundSource = true
            }
        }
        if(!foundSource) {
            playerCargo.addWeapons(spec.weaponId, 1)
        }

        weaponSlotSources[slot.id] = null
        variant.clearSlot(slot.id)*/

        //val slottedFighters = ReflectionUtils.invoke(("getSlottedFighters"), shipDisplay) as? Map<String, FighterPickerDialog.o>
        /*val slottedWeapons = ReflectionUtils.invoke(("getSlottedWeapons"), shipDisplay) as? Map<String, WeaponPickerDialog.o>

        if(!slottedWeapons.isNullOrEmpty()) {
            val weapon = slottedWeapons.get(variant.hullVariantId + "_" + slot.id)
            if(weapon == null) { Global.getLogger(this.javaClass).info("wep was null") }
            else {
                val thisWeapon = weapons.find { it.id == weapon.id && it.submarket.specId == weapon.submarket?.specId}
                if(thisWeapon!=null) {
                    thisWeapon.quantity++
                }
            }
        }*/

        shipDisplay.invoke(
            "clearSlot", slot//Weapon slot
            , variant as HullVariantSpec
        )

        //weapon.quantity++
    }

    override fun getAvailableWeapons(): List<AutofitPlugin.AvailableWeapon> {
        return weapons.toList()
    }

    fun addAvailableWeapon(weaponSpec: WeaponSpecAPI, count: Int, cargo: CargoAPI, submarket: SubmarketAPI?) {
        val weap = WeaponPickerDialog.o(
            count,
            cargo,
            weaponSpec as BaseWeaponSpec,
            weaponSpec.weaponId,
            submarket as? Submarket,
            coreUI
        )

        if (submarket == null) {//Player cargo
            weapons.add(0, weap)//Put first
        } else {//Submarket
            if (!submarket.plugin.isFreeTransfer)
                weap.setPrice(weaponSpec.baseValue * (1 + submarket.tariff))
            weapons.add(weap)//Put last
        }
    }

    override fun getAvailableFighters(): List<AutofitPlugin.AvailableFighter> {
        return fighters.toList()
    }

    fun addAvailableFighter(fighterSpec: FighterWingSpecAPI, count: Int, cargo: CargoAPI, submarket: SubmarketAPI?) {
        val wing = FighterPickerDialog.o(
            count,
            cargo,
            fighterSpec as FighterWingSpec,
            fighterSpec.id,
            submarket as? Submarket,
            coreUI
        )

        if (submarket == null) {//Player cargo
            fighters.add(0, wing)//Put first
        } else {//Submarket
            if (!submarket.plugin.isFreeTransfer)
                wing.setPrice(fighterSpec.baseValue * (1 + submarket.tariff))
            fighters.add(wing)//Put last
        }
    }

    override fun isPriority(weapon: WeaponSpecAPI): Boolean {
        return faction.isWeaponPriority(weapon.weaponId)
    }

    override fun isPriority(wing: FighterWingSpecAPI): Boolean {
        return faction.isFighterPriority(wing.id)
    }

    override fun getAvailableHullmods(): MutableList<String> {
        return hullmods
    }

    override fun syncUIWithVariant(variant: ShipVariantAPI) {}
    override fun getShip(): ShipAPI {
        //throw UnsupportedOperationException("Not implemented1")
        return ship
    }

    override fun getFaction(): FactionAPI {
        return faction
    }

    override fun isAllowSlightRandomization(): Boolean = false
    override fun isPlayerCampaignRefit(): Boolean = true//If in campaign, true. If not, false?
    override fun canAddRemoveHullmodInPlayerCampaignRefit(modId: String): Boolean {//If this mod can be removed at this time.
        //if (market != null) {
        //     return true
        // } else {
        val mod = Global.getSettings().getHullModSpec(modId) ?: return false
        return mod.effect.canBeAddedOrRemovedNow(ship, market, CampaignUIAPI.CoreUITradeMode.OPEN)
        //}
    }

    override fun getMarket(): MarketAPI? {
        return market
    }

    override fun getFleetMember(): FleetMemberAPI {
        return fleetMember
    }
}