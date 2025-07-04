package fleetBuilder.util

import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.util.MISC.showError
import starficz.ReflectionUtils.getFieldsMatching

object ModifyInternalVariants {

    const val safteyPrefix = "FBV_"//FBT = Fleet Builder Variant
    private fun getInternalVariantsMap(): HashMap<String, HullVariantSpec> {
        val eep = Class.forName("com.fs.starfarer.loading.new")
        val erp = eep.getFieldsMatching(fieldAccepts = Map::class.java).firstOrNull() ?: return hashMapOf()
        @Suppress("UNCHECKED_CAST")
        return erp.get(eep) as HashMap<String, HullVariantSpec>
    }

    private fun getInternalVariant(variantId: String): HullVariantSpec? {
        val variantsMap = getInternalVariantsMap()
        return variantsMap[variantId]?.clone()
    }

    private fun setInternalVariant(variant: HullVariantSpec) {
        val variantsMap = getInternalVariantsMap()
        variantsMap[variant.hullVariantId] = variant
    }

    fun getModifiedInternalVariant(variantId: String): HullVariantSpec? {
        try {
            val variantsMap = getInternalVariantsMap()
            val variant = variantsMap["$safteyPrefix$variantId"]?.clone() ?: return null
            variant.hullVariantId = variant.hullVariantId.removePrefix(safteyPrefix)
            return variant

        } catch (e: Exception) {
            showError("Failed to get modified internal variant", e)
            return null
        }
    }

    fun setModifiedInternalVariant(variant: HullVariantSpec) {
        try {
            val variantClone = variant.clone()
            variantClone.hullVariantId = "$safteyPrefix${variantClone.hullVariantId}"

            val variantsMap = getInternalVariantsMap()
            variantsMap[variantClone.hullVariantId] = variantClone

        } catch (e: Exception) {
            showError("Failed to set modified internal variant", e)
        }
    }

    fun clearAllModifiedVariants() {
        val variantsMap: HashMap<String, HullVariantSpec>
        try {
            variantsMap = getInternalVariantsMap()
        } catch (e: Exception) {
            showError("Failed to clear all modified internal variants", e)
            return
        }
        val keysToRemove = variantsMap.keys.filter { it.startsWith(safteyPrefix) }
        keysToRemove.forEach { variantsMap.remove(it) }
    }
}