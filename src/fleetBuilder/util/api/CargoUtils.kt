package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI

object CargoUtils {

    // Calculate number of supplies needed to reach maxCargoFraction
    // Cap quantity to 100% remaining cargo space, don't go below 0 either
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