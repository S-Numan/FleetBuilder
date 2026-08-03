package fleetBuilder.core.integration.feature

import com.fs.starfarer.api.Global
import com.fs.starfarer.campaign.CampaignEngine

internal object RemoveNullGoalVariants {
    fun removeNullGoalVariants() {
        var count = 0

        val camp = CampaignEngine.getInstance()
        val svd = camp.savedVariantData
        for (spec in Global.getSettings().allShipHullSpecs) {
            val variantArray = svd.getVisible(spec.hullId)
            for (i in variantArray.variants.indices) {
                val variantResolver = variantArray.variants[i] ?: continue
                if (variantResolver.getVariant() == null) {
                    Global.getLogger(this.javaClass).warn("- Found null variant with ID ${variantResolver.variantIdIfStock}")
                    variantArray.remove(i, variantResolver.variantIdIfStock, svd.variantMap)
                    count++
                }
            }
        }

        if (count > 0)
            Global.getLogger(this.javaClass).warn("Purged $count missing variants")
    }
}