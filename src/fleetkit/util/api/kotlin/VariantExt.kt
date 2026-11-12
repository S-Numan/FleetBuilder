package fleetkit.util.api.kotlin

import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetkit.serialization.variant.DataVariant
import fleetkit.serialization.variant.VariantSettings
import fleetkit.util.api.VariantUtils

/**
 * Creates a copy of this variant with the specified settings.
 */
fun ShipVariantAPI.clone(settings: VariantSettings): ShipVariantAPI {
    return DataVariant.cloneVariant(this, settings = settings)
}

fun ShipVariantAPI.isEquivalentTo(
    other: ShipVariantAPI,
    options: VariantUtils.CompareOptions = VariantUtils.CompareOptions()
): Boolean {
    return VariantUtils.compareVariantContents(
        this,
        other,
        options
    )
}
