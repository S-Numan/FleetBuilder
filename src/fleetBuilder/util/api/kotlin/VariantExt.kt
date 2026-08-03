package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.api.VariantUtils

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
