package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.SpecialItemData

object CargoUtils {

    /**
     * Returns the manufacturer of the item this stack represents.
     *
     * Due to the way the game handles special items, this function may return null for some items when you might expect it to not.
     * @return The manufacturer of the item, or null if the item is not recognized.
     */
    fun getItemTech(cargo: CargoStackAPI): String? {
        return when {
            cargo.isWeaponStack -> cargo.weaponSpecIfWeapon.manufacturer
            cargo.isFighterWingStack -> cargo.fighterWingSpecIfWing.variant.hullSpec.manufacturer
            cargo.hullModSpecIfHullMod != null -> cargo.hullModSpecIfHullMod.manufacturer
            cargo.specialDataIfSpecial != null -> getSpecialItemTech(cargo.specialDataIfSpecial)
            cargo.specialItemSpecIfSpecial != null -> cargo.specialItemSpecIfSpecial.manufacturer
            else -> null
        }
    }

    private fun getSpecialItemTech(data: SpecialItemData): String? {
        return when (data.id) {
            "fighter_bp" ->
                Global.getSettings().allFighterWingSpecs.find { it.id == data.data }?.variant?.hullSpec?.manufacturer
            "weapon_bp" ->
                Global.getSettings().allWeaponSpecs.find { it.weaponId == data.data }?.manufacturer
            "ship_bp" ->
                Global.getSettings().allShipHullSpecs.find { it.hullId == data.data }?.manufacturer
            "modspec" ->
                Global.getSettings().allHullModSpecs.find { it.id == data.data }?.manufacturer

            else -> null
        }
    }

    /**
     * Calculate number of supplies needed to reach maxCargoFraction
     *
     * Cap quantity to 100% remaining cargo space, don't go below 0 either
     */
    fun getFractionHoldableSupplies(cargo: CargoAPI, maxCargoFraction: Float = 1f): Int {
        var total = Math.min(
            cargo.getSpaceLeft(),
            Math.max(
                0f,
                ((cargo.getMaxCapacity() * maxCargoFraction) - cargo.getSupplies())
            )
        ).toInt()

        // Adjust for cargo space supplies take up (if modded, only use 1 in vanilla)
        val spacePerSupply = Global.getSector().economy
            .getCommoditySpec("supplies").cargoSpace
        if (spacePerSupply > 0)
            total = (total / spacePerSupply).toInt()

        if (total < 0)
            total = 0

        return total
    }
}