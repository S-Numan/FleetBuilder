package fleetBuilder.ui.autofit

import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.variants.MissingElements

data class AutofitSpec @JvmOverloads constructor(
    val variant: ShipVariantAPI,
    var name: String,
    var description: String? = null,
    var spriteId: String,
    var missingFromVariant: MissingElements = MissingElements(),
)
