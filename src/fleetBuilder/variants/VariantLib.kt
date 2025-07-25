package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.getCompatibleDLessHullId
import fleetBuilder.util.getEffectiveHullId
import fleetBuilder.variants.LoadoutManager.getAnyVariantsForHullspec

object VariantLib {

    private lateinit var allDMods: Set<String>
    private lateinit var allHiddenEverywhereMods: Set<String>
    private lateinit var variantMap: Map<String, List<ShipVariantAPI>>
    private lateinit var hullIDSet: Set<String>
    private var init = false
    fun Loaded() = init

    fun onApplicationLoad() {
        init = true

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
        val tempVariantMap: MutableMap<String, MutableList<ShipVariantAPI>> = mutableMapOf()
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
            //Checks if tempVariantMap contains the key hullId.
            //    If yes: returns the existing list.
            //    If no: creates a new mutableListOf() and puts it into the map under hullId
            tempVariantMap.getOrPut(hullId) { mutableListOf() }.add(variant)
        }

        variantMap = tempVariantMap.mapValues { it.value.toList() }

        hullIDSet = Global.getSettings().allShipHullSpecs.map { it.hullId }.toSet()
    }

    fun getAllDMods(): Set<String> = allDMods
    fun getAllHiddenEverywhereMods(): Set<String> = allHiddenEverywhereMods
    fun getHullIDSet(): Set<String> = hullIDSet

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
        insertVariant1: ShipVariantAPI,
        insertVariant2: ShipVariantAPI,
        compareFlux: Boolean = true,
        compareWeapons: Boolean = true,
        compareWeaponGroups: Boolean = true,
        compareWings: Boolean = true,
        compareHullMods: Boolean = true,
        compareBuiltInHullMods: Boolean = true,
        compareHiddenHullMods: Boolean = true,
        compareDMods: Boolean = true,
        compareSMods: Boolean = true,
        convertSModsToRegular: Boolean = false,
        compareModules: Boolean = true,
        compareTags: Boolean = false,
        useEffectiveHull: Boolean = false
    ): Boolean {
        val variant1 = insertVariant1.clone()
        val variant2 = insertVariant2.clone()

        if (useEffectiveHull) {
            if (variant1.hullSpec.getEffectiveHullId() != variant2.hullSpec.getEffectiveHullId())
                return false
        } else if (variant1.hullSpec.hullId != variant2.hullSpec.hullId)
            return false

        if (compareFlux) {
            if (variant1.numFluxVents != variant2.numFluxVents || variant1.numFluxCapacitors != variant2.numFluxCapacitors)
                return false
        }
        if (compareWeapons) {
            if (!compareWeaponGroups) {
                variant1.autoGenerateWeaponGroups()
                variant2.autoGenerateWeaponGroups()
            }
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
            if (!compareVariantHullMods(
                    variant1, variant2,
                    compareHiddenHullMods = compareHiddenHullMods,
                    compareDMods = compareDMods,
                    compareSMods = compareSMods,
                    convertSModsToRegular = convertSModsToRegular,
                    compareBuiltInHullMods = compareBuiltInHullMods
                )
            ) return false
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
                if (!compareVariantContents(
                        variant1.getModuleVariant(slot),
                        variant2.getModuleVariant(slot),
                        compareFlux = compareFlux,
                        compareWeapons = compareWeapons,
                        compareWeaponGroups = compareWeaponGroups,
                        compareWings = compareWings,
                        compareHullMods = compareHullMods,
                        compareBuiltInHullMods = compareBuiltInHullMods,
                        compareHiddenHullMods = compareHiddenHullMods,
                        compareDMods = compareDMods,
                        compareSMods = compareSMods,
                        convertSModsToRegular = convertSModsToRegular,
                        compareModules = true,
                        compareTags = compareTags,
                        useEffectiveHull = useEffectiveHull
                    )
                ) {
                    return false
                }
            }
        }

        return true
    }

    fun compareVariantHullMods(
        insertVariant1: ShipVariantAPI,
        insertVariant2: ShipVariantAPI,
        compareHiddenHullMods: Boolean = true,
        compareDMods: Boolean = true,
        compareSMods: Boolean = true,
        convertSModsToRegular: Boolean = false,
        compareBuiltInHullMods: Boolean = true
    ): Boolean {
        val variant1 = insertVariant1.clone()
        val variant2 = insertVariant2.clone()

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

        if (!compareSMods || convertSModsToRegular) {
            variant1.sModdedBuiltIns.clear()
            variant2.sModdedBuiltIns.clear()

            processSModsForComparison(variant1, convertSModsToRegular)
            processSModsForComparison(variant2, convertSModsToRegular)
        }

        val variant1HullMods = variant1.hullMods.toMutableSet()
        val variant2HullMods = variant2.hullMods.toMutableSet()

        if (!compareBuiltInHullMods) {
            variant1.sModdedBuiltIns.clear()
            variant2.sModdedBuiltIns.clear()

            val toRemove1 = variant1HullMods.filter { it in variant1.hullSpec.builtInMods }
            val toRemove2 = variant2HullMods.filter { it in variant2.hullSpec.builtInMods }

            toRemove1.forEach { modId ->
                variant1.sMods.remove(modId)
                variant1HullMods.remove(modId)
            }

            toRemove2.forEach { modId ->
                variant2.sMods.remove(modId)
                variant2HullMods.remove(modId)
            }
        }

        val variantModsEqual = hullModSetsEqual(variant1HullMods, variant2HullMods) &&
                hullModSetsEqual(variant1.sMods, variant2.sMods) &&
                hullModSetsEqual(variant1.permaMods, variant2.permaMods) &&
                hullModSetsEqual(variant1.sModdedBuiltIns, variant2.sModdedBuiltIns) &&
                hullModSetsEqual(variant1.suppressedMods, variant2.suppressedMods)

        return variantModsEqual
    }

    fun processSModsForComparison(variant: ShipVariantAPI, convert: Boolean) {
        val sModsCopy = variant.sMods.toSet()
        sModsCopy.forEach { sMod ->
            variant.completelyRemoveMod(sMod)
            if (convert) {
                variant.addMod(sMod)
            }
        }
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

    fun createErrorVariant(displayName: String = ""): ShipVariantAPI {
        var tempVariant: ShipVariantAPI? = null
        try {
            tempVariant = Global.getSettings().getVariant(Global.getSettings().getString("errorShipVariant"))
        } catch (_: Exception) {
        }
        if (tempVariant == null)
            tempVariant = Global.getSettings().getVariant(Global.getSettings().allVariantIds.first())
        if (tempVariant == null) throw Exception("No variants anywhere? How?")

        tempVariant = tempVariant.clone()

        if (displayName.isNotEmpty())
            tempVariant.setVariantDisplayName("ERR:$displayName")
        else
            tempVariant.setVariantDisplayName("ERROR")

        tempVariant.addTag("ERROR")

        return tempVariant
    }
}