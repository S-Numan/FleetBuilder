package fleetkit.features.autofit.ui

import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetkit.features.autofit.shipDirectory.ShipDirectory
import fleetkit.serialization.MissingContent

data class AutofitSpec @JvmOverloads constructor(
    val variant: ShipVariantAPI,
    val source: ShipDirectory?, //If from vanilla, this will be null
    var desiredIndexInMenu: Int = 0,
    val description: String = "",
    var missing: MissingContent = MissingContent(),
)
