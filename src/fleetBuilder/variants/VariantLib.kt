package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import fleetBuilder.util.getCompatibleDLessHullId
import fleetBuilder.util.getEffectiveHullId
import fleetBuilder.variants.LoadoutManager.getAnyVariantsForHullspec

object VariantLib {

    private lateinit var allDMods: Set<String>
    private lateinit var allHiddenEverywhereMods: Set<String>
    private lateinit var variantMap: MutableMap<String, MutableList<ShipVariantAPI>>

    fun onApplicationLoad() {
        allDMods = Global.getSettings().allHullModSpecs
            .asSequence()
            .filter { it.hasTag("dmod") }
            .map { it.id }
            .toSet()

        allHiddenEverywhereMods = Global.getSettings().allHullModSpecs
            .asSequence()
            .filter { it.isHiddenEverywhere }
            .map { it.id }
            .toSet()

        //val variantIdMap = Global.getSettings().hullIdToVariantListMap//Does not contain every variant
        variantMap = mutableMapOf()
        for (variantId in Global.getSettings().allVariantIds) {
            val variant = Global.getSettings().getVariant(variantId) ?: continue
            if (variant.source != VariantSource.STOCK) continue
            val hullId = variant.hullSpec?.getEffectiveHullId() ?: continue

            //Are modules automatically put in every variant?
            /*variant.moduleSlots.forEach { slot ->
                val moduleVariant = Global.getSettings().getVariant(variant.stationModules[slot].orEmpty())
                if(moduleVariant != null) {
                    variant.setModuleVariant(slot, moduleVariant)
                } else {
                    Global.getLogger(this.javaClass).error("module variant with id ${variant.stationModules[slot]} was null")
                }
            }*/

            //getOrPut
            //Checks if variantMap contains the key hullId.
            //    If yes: returns the existing list.
            //    If no: creates a new mutableListOf() and puts it into the map under hullId
            val variantsForHull = variantMap.getOrPut(hullId) { mutableListOf() }
            variantsForHull.add(variant)
        }
    }

    fun getAllDMods(): Set<String> = allDMods
    fun getAllHiddenEverywhereMods(): Set<String> = allHiddenEverywhereMods

    fun getCoreVariantsForEffectiveHullspec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        return variantMap[hullSpec.getEffectiveHullId()].orEmpty()
    }

    fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {

        //Here sets the variant ID after a variant is saved.

        val idIfNone = makeVariantID(member.variant)
        val possibleVariants = getAnyVariantsForHullspec(member.hullSpec)

        val matchingVariant = possibleVariants.find { candidate ->
            compareVariantContents(candidate, member.variant)
        }

        member.variant.hullVariantId = when {
            matchingVariant != null -> {
                member.variant.moduleSlots.forEach { slot ->
                    member.variant.getModuleVariant(slot).hullVariantId =
                        matchingVariant.getModuleVariant(slot).hullVariantId
                }
                matchingVariant.hullVariantId
            }

            possibleVariants.any { it.hullVariantId == idIfNone } -> {
                member.variant.moduleSlots.forEach { slot ->
                    member.variant.getModuleVariant(slot).hullVariantId = "${idIfNone}_c_$slot"
                }
                "${idIfNone}_c"
            }

            else -> {
                member.variant.moduleSlots.forEach { slot ->
                    member.variant.getModuleVariant(slot).hullVariantId = "${idIfNone}_$slot"
                }
                idIfNone
            }
        }
    }

    @JvmOverloads
    fun compareVariantContents(
        variant1: ShipVariantAPI,
        variant2: ShipVariantAPI,
        compareFlux: Boolean = true,
        compareWeapons: Boolean = true,
        compareWings: Boolean = true,
        compareHullMods: Boolean = true,
        compareHiddenHullMods: Boolean = true,
        compareDMods: Boolean = true,
        compareModules: Boolean = true,
        compareTags: Boolean = false,
        useEffectiveHull: Boolean = false
    ): Boolean {
        if (useEffectiveHull && variant1.hullSpec.getEffectiveHullId() != variant2.hullSpec.getEffectiveHullId())
            return false
        else if (variant1.hullSpec.hullId != variant2.hullSpec.hullId)
            return false

        if (compareFlux) {
            if (variant1.numFluxVents != variant2.numFluxVents || variant1.numFluxCapacitors != variant2.numFluxCapacitors)
                return false
        }
        if (compareWeapons) {
            if (variant1.fittedWeaponSlots.size != variant2.fittedWeaponSlots.size) return false
            for (slotId in variant1.fittedWeaponSlots) {
                if (variant1.getWeaponId(slotId) != variant2.getWeaponId(slotId))
                    return false
            }
        }
        if (compareWings) {
            if (variant1.fittedWings.size != variant2.fittedWings.size) return false
            for (i in variant1.fittedWings.indices) {
                if (variant1.getWingId(i) != variant2.getWingId(i)) {
                    return false
                }
            }
        }
        if (compareHullMods) {
            fun hullModSetsEqual(
                set1: Set<String>,
                set2: Set<String>,
            ): Boolean {
                fun filterMods(mods: Set<String>) = mods.filterNot { modId ->
                    (!compareHiddenHullMods && modId in allHiddenEverywhereMods) ||
                            (!compareDMods && modId in allDMods)
                }.toSet()

                return filterMods(set1) == filterMods(set2)
            }

            val variantModsEqual = hullModSetsEqual(variant1.hullMods.toSet(), variant2.hullMods.toSet()) &&
                    hullModSetsEqual(variant1.sMods, variant2.sMods) &&
                    hullModSetsEqual(variant1.permaMods, variant2.permaMods) &&
                    hullModSetsEqual(variant1.sModdedBuiltIns, variant2.sModdedBuiltIns) &&
                    hullModSetsEqual(variant1.suppressedMods, variant2.suppressedMods)

            if (!variantModsEqual) return false
        }
        if (compareTags) {
            if (variant1.tags.size != variant2.tags.size) return false
            for (tag in variant1.tags) {
                if (!variant2.tags.contains(tag))
                    return false
            }
        }

        if (compareModules) {
            variant1.moduleSlots.forEach { slot ->
                if (!compareVariantContents(variant1.getModuleVariant(slot), variant2.getModuleVariant(slot))) {
                    return false
                }
            }
        }

        return true
    }

    fun makeVariantID(variant: ShipVariantAPI): String {
        val hullId = variant.hullSpec.getCompatibleDLessHullId()
        val cleanName = variant.displayName
            .replace(" ", "_")                       // replace spaces with underscores
            .replace(Regex("[^A-Za-z0-9_-]"), "")   // remove anything not a-z, A-Z, 0-9, dash, or underscore
            .trim('.')                                          // remove leading/trailing dots
            .trim()                                                     // remove leading/trailing whitespace

        return "${hullId}_$cleanName"
    }
}