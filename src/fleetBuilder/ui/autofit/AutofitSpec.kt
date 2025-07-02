package fleetBuilder.ui.autofit

import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.variants.MissingElements

data class AutofitSpec @JvmOverloads constructor(
    //val modId: String,
    //val modName: String,
    //val id: String,
    val variant: ShipVariantAPI,
    //@Deprecated("Use hullIds instead")
    //val hullId: String,
    //val hullIds: List<String> = listOf(hullId),
    var name: String,
    var description: String? = null,
    var spriteId: String,
    var missingFromVariant: MissingElements = MissingElements(),
)
