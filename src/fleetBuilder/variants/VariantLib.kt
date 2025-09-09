package fleetBuilder.variants

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.loading.WeaponSpecAPI
import fleetBuilder.util.completelyRemoveMod
import fleetBuilder.util.getCompatibleDLessHullId
import fleetBuilder.util.getEffectiveHullId
import fleetBuilder.variants.LoadoutManager.getAnyVariantsForHullspec

object VariantLib {

    private lateinit var allDMods: Set<String>
    private lateinit var allHiddenEverywhereMods: Set<String>
    private lateinit var effectiveVariantMap: Map<String, List<ShipVariantAPI>>
    private lateinit var hullIDSet: Set<String>
    private lateinit var errorVariantHullID: String
    private lateinit var IDToHullSpec: Map<String, ShipHullSpecAPI>
    private lateinit var IDToWing: Map<String, FighterWingSpecAPI>
    private lateinit var IDToWeapon: Map<String, WeaponSpecAPI>
    private lateinit var IDToHullMod: Map<String, HullModSpecAPI>
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

        effectiveVariantMap = tempVariantMap.mapValues { it.value.toList() }

        hullIDSet = Global.getSettings().allShipHullSpecs.map { it.hullId }.toSet()

        errorVariantHullID = createErrorVariant().hullSpec.hullId

        IDToHullSpec = Global.getSettings().allShipHullSpecs.associateBy { it.hullId }

        IDToWing = Global.getSettings().allFighterWingSpecs.associateBy { it.id }
        IDToHullMod = Global.getSettings().allHullModSpecs.associateBy { it.id }
        IDToWeapon = Global.getSettings().actuallyAllWeaponSpecs.associateBy { it.weaponId }
    }

    fun getAllDMods(): Set<String> = allDMods
    fun getAllHiddenEverywhereMods(): Set<String> = allHiddenEverywhereMods
    fun getHullSpec(hullId: String) = IDToHullSpec[hullId]
    fun getHullIDSet(): Set<String> = IDToHullSpec.keys
    fun getFighterWingSpec(wingId: String) = IDToWing[wingId]
    fun getFighterWingIDSet(): Set<String> = IDToWing.keys
    fun getWeaponSpec(weaponId: String) = IDToWeapon[weaponId]
    fun getActuallyAllWeaponSpecs(): Set<String> = IDToWeapon.keys
    fun getHullModSpec(hullModId: String) = IDToHullMod[hullModId]
    fun getHullModIDSet(): Set<String> = IDToHullMod.keys

    fun getCoreVariantsForEffectiveHullspec(hullSpec: ShipHullSpecAPI): List<ShipVariantAPI> {
        return effectiveVariantMap[hullSpec.getEffectiveHullId()].orEmpty()
    }

    fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {

        //Here sets the variant ID after a variant is saved.

        val idIfNone = makeVariantID(member.variant)
        val possibleVariants = getAnyVariantsForHullspec(member.hullSpec)

        val matchingVariant = possibleVariants.find { candidate ->
            compareVariantContents(candidate, member.variant, CompareOptions(tags = false))
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

    data class CompareOptions(
        val flux: Boolean = true,
        val weapons: Boolean = true,
        val weaponGroups: Boolean = true,
        val wings: Boolean = true,
        val hullMods: Boolean = true,
        val builtInHullMods: Boolean = true, // Does not include built in DMods
        val hiddenHullMods: Boolean = true,
        val dMods: Boolean = true,
        val sMods: Boolean = true,
        val modules: Boolean = true,
        val tags: Boolean = true,
        val hull: Boolean = true,
        val convertSModsToRegular: Boolean = false,
        val useEffectiveHull: Boolean = false,
    ) {
        companion object {
            fun allFalse(
                flux: Boolean = false,
                weapons: Boolean = false,
                weaponGroups: Boolean = false,
                wings: Boolean = false,
                hullMods: Boolean = false,
                builtInHullMods: Boolean = false,
                hiddenHullMods: Boolean = false,
                dMods: Boolean = false,
                sMods: Boolean = false,
                modules: Boolean = false,
                tags: Boolean = false,
                hull: Boolean = false,
                convertSModsToRegular: Boolean = false,
                useEffectiveHull: Boolean = false,
            ) = CompareOptions(
                flux = flux, weapons = weapons, weaponGroups = weaponGroups, wings = wings, hullMods = hullMods, builtInHullMods = builtInHullMods, hiddenHullMods = hiddenHullMods,
                dMods = dMods, sMods = sMods, modules = modules, tags = tags, hull = hull, convertSModsToRegular = convertSModsToRegular, useEffectiveHull = useEffectiveHull
            )
        }
    }


    @JvmOverloads
    fun compareVariantContents(
        insertVariant1: ShipVariantAPI,
        insertVariant2: ShipVariantAPI,
        options: CompareOptions = CompareOptions()
    ): Boolean {
        val variant1 = insertVariant1.clone()
        val variant2 = insertVariant2.clone()

        if (options.hull) {
            if (options.useEffectiveHull) {
                if (variant1.hullSpec.getEffectiveHullId() != variant2.hullSpec.getEffectiveHullId())
                    return false
            } else if (variant1.hullSpec.hullId != variant2.hullSpec.hullId)
                return false
        }

        if (options.flux) {
            if (variant1.numFluxVents != variant2.numFluxVents || variant1.numFluxCapacitors != variant2.numFluxCapacitors)
                return false
        }

        fun slotsMatch(slots1: List<String>, slots2: List<String>): Boolean {
            val set1 = slots1.filter { it.isNotEmpty() }.toSet()
            val set2 = slots2.filter { it.isNotEmpty() }.toSet()
            return set1 == set2
        }
        if (options.weaponGroups) {
            // Want to be extra careful here in case something is null

            if (variant1.weaponGroups.count { !it.slots.isNullOrEmpty() } != variant2.weaponGroups.count { !it.slots.isNullOrEmpty() }) return false

            variant1.weaponGroups.forEachIndexed { i, g1 ->
                val g2 = variant2.weaponGroups[i]

                val slots1 = g1?.slots ?: emptyList()
                val slots2 = g2?.slots ?: emptyList()

                // both empty? skip
                if (slots1.isEmpty() && slots2.isEmpty()) return@forEachIndexed

                // mismatch in slots
                if (!slotsMatch(slots1, slots2)) return false

                // other property checks
                if (g1?.type != g2?.type) return false
                if (g1?.isAutofireOnByDefault != g2?.isAutofireOnByDefault) return false
            }
        }
        if (options.weapons) {
            if (variant1.fittedWeaponSlots.size != variant2.fittedWeaponSlots.size) return false
            for (slotId in variant1.fittedWeaponSlots) {
                if (variant1.getWeaponId(slotId) != variant2.getWeaponId(slotId))
                    return false
            }
        }
        if (options.wings) {
            if (variant1.fittedWings.size != variant2.fittedWings.size) return false
            for (i in variant1.fittedWings.indices) {
                if (variant1.getWingId(i) != variant2.getWingId(i)) {
                    return false
                }
            }
        }
        if (options.hullMods) {
            if (!compareVariantHullMods(
                    variant1, variant2,
                    options
                )
            ) return false
        }
        if (options.tags) {
            if (variant1.tags.size != variant2.tags.size) return false
            for (tag in variant1.tags) {
                if (!variant2.tags.contains(tag))
                    return false
            }
        }

        if (options.modules) {
            variant1.moduleSlots.forEach { slot ->
                if (!compareVariantContents(
                        variant1.getModuleVariant(slot),
                        variant2.getModuleVariant(slot),
                        options
                    )
                ) {
                    return false
                }
            }
        }

        return true
    }

    @JvmOverloads
    fun compareVariantHullMods(
        insertVariant1: ShipVariantAPI,
        insertVariant2: ShipVariantAPI,
        options: CompareOptions = CompareOptions(),
    ): Boolean {
        val variant1 = insertVariant1.clone()
        val variant2 = insertVariant2.clone()

        fun hullModSetsEqual(
            set1: Set<String>,
            set2: Set<String>,
        ): Boolean {
            fun filterMods(mods: Set<String>) = mods.filterNot { modId ->
                (!options.hiddenHullMods && modId in allHiddenEverywhereMods) ||
                        (!options.dMods && modId in allDMods)
            }.toSet()

            return filterMods(set1) == filterMods(set2)
        }

        if (!options.sMods || options.convertSModsToRegular) {
            processSModsForComparison(variant1, options.convertSModsToRegular)
            processSModsForComparison(variant2, options.convertSModsToRegular)
        }

        val variant1HullMods = variant1.hullMods.toMutableSet()
        val variant2HullMods = variant2.hullMods.toMutableSet()

        if (!options.builtInHullMods) {
            val toRemove1 = variant1HullMods.filter { it in variant1.hullSpec.builtInMods && it !in allDMods }
            val toRemove2 = variant2HullMods.filter { it in variant2.hullSpec.builtInMods && it !in allDMods }

            toRemove1.forEach { modId ->
                variant1.sMods.remove(modId)
                variant1.sModdedBuiltIns.remove(modId)
                variant1HullMods.remove(modId)
            }

            toRemove2.forEach { modId ->
                variant2.sMods.remove(modId)
                variant2.sModdedBuiltIns.remove(modId)
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
        val sModsCopy = (variant.sMods + variant.sModdedBuiltIns).toSet()
        sModsCopy.forEach { sMod ->
            val isBuiltIn = sMod in variant.hullSpec.builtInMods
            variant.completelyRemoveMod(sMod)
            if (convert && !isBuiltIn) {
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

    val errorTag = "FB_ERR"

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

        tempVariant.addTag(errorTag)

        return tempVariant
    }

    fun getErrorVariantHullID(): String {
        return errorVariantHullID
    }
}