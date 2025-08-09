package fleetBuilder.ui.autofit

import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.variants.MissingElements
import fleetBuilder.variants.ShipDirectory

data class AutofitSpec @JvmOverloads constructor(
    val variant: ShipVariantAPI,
    val source: ShipDirectory?, //If from vanilla, this will be null
    val description: String = "",
    var missing: MissingElements = MissingElements(),
)
