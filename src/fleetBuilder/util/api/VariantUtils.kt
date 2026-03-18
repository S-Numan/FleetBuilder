package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.*
import org.magiclib.kotlin.getBuildInBonusXP

object VariantUtils {

    /**
     * Returns all source mods from a variant. Including the hullspec, all weapons, wings, hullmods, and the contents of modules.
     *
     * @param variant the variant to get the source mods from
     * @param filterParsed whether to filter the parsed variant data with settings. Most notably, enabling this to true will exclude always present hullmods such as the 'rat_controller' and similar. (default: false)
     * @return a set of all source mods from the variant
     */
    @JvmOverloads
    fun getAllSourceModsFromVariant(variant: ShipVariantAPI, filterParsed: Boolean = false): Set<ModSpecAPI> {
        return getAllSourceModsFromVariant(DataVariant.getVariantDataFromVariant(variant, filterParsed = filterParsed))
    }

    /**
     * Returns all source mods from a variant. Including the hullspec, all weapons, wings, hullmods, and the contents of modules.
     *
     * @param variant the variant to get the source mods from
     * @param settings the settings to use when checking for source mods (default: VariantSettings())
     * @return a set of all source mods from the variant
     */
    fun getAllSourceModsFromVariant(
        variant: ShipVariantAPI,
        settings: VariantSettings = VariantSettings()
    ): Set<ModSpecAPI> {
        return getAllSourceModsFromVariant(DataVariant.getVariantDataFromVariant(variant, settings))
    }

    fun getAllSourceModsFromVariant(data: DataVariant.ParsedVariantData): Set<ModSpecAPI> {
        val sourceMods = mutableSetOf<ModSpecAPI>()

        // Check modules first

        data.moduleVariants.forEach { (_, module) ->
            sourceMods.addAll(getAllSourceModsFromVariant(module))
        }

        // HullSpec

        LookupUtil.getHullSpec(data.hullId)?.let { hullSpec ->
            hullSpec.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        // HullMods
        for (mod in data.hullMods) {
            LookupUtil.getHullModSpec(mod)?.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        // Weapons
        for (group in data.weaponGroups) {
            group.weapons.forEach { (slot, weaponId) ->
                LookupUtil.getWeaponSpec(weaponId)?.sourceMod?.let { sm ->
                    sourceMods.add(sm)
                }
            }
        }

        // Fighter Wings
        for (wing in data.wings) {
            LookupUtil.getFighterWingSpec(wing)?.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        return sourceMods
    }

    // Returns full amount of xp if this hullmod were to be added to this variant
    fun getHullModBuildInBonusXP(
        variant: ShipVariantAPI,
        modID: String,
    ): Float {
        val defaultBonusXP = Global.getSector().playerStats.bonusXPForSpendingStoryPointBeforeSpendingIt.toFloat()
        if (variant.hullSpec.builtInMods.contains(modID)) {
            return defaultBonusXP
        } else {
            val sMod = Global.getSettings().getHullModSpec(modID)
            return defaultBonusXP * sMod.getBuildInBonusXP(variant.hullSize) // getBuildInBonusXP returns a fraction
        }
    }

    fun isVariantKnownToPlayer(variant: ShipVariantAPI): Boolean {
        if (variant.hullSpec.hasTag("codex_unlockable") && !SharedUnlockData.get().isPlayerAwareOfShip(variant.hullSpec.hullId)) {
            return false
        }
        if (!variant.hullSpec.hasTag("codex_unlockable") && (variant.hullSpec.hints.contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX) || variant.hullSpec.hasTag(Tags.HIDE_IN_CODEX)
                    || variant.hints.contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX) || variant.hasTag(Tags.HIDE_IN_CODEX))
        ) {
            return false
        }
        return true
    }

    fun makeVariantID(variant: ShipVariantAPI): String {
        val hullId = variant.hullSpec.getCompatibleDLessHullId(true)
        return makeVariantID(hullId, variant.displayName)
    }

    fun makeVariantID(hullId: String, displayName: String): String {
        val cleanName = displayName
            .replace(" ", "_")                       // replace spaces with underscores
            .replace(Regex("[^A-Za-z0-9_-]"), "")   // remove anything not a-z, A-Z, 0-9, dash, or underscore
            .trim('.')                                          // remove leading/trailing dots
            .trim()                                                     // remove leading/trailing whitespace

        return "${hullId}_$cleanName"
    }

    private const val errorTag = "FB_ERR"
    fun getFBVariantErrorTag() = errorTag

    fun isErrorVariant(variant: ShipVariantAPI): Boolean {
        return variant.hasTag(errorTag)
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

        tempVariant.addTag(errorTag)

        return tempVariant
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
        val permaMods: Boolean = true,
        val modules: Boolean = true,
        val tags: Boolean = true,
        val hull: Boolean = true,
        val convertSModsToRegular: Boolean = false,
        val useEffectiveHull: Boolean = false,
        val compareTemporaryTags: Boolean = false,
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
                permaMods: Boolean = false,
                modules: Boolean = false,
                tags: Boolean = false,
                hull: Boolean = false,
                convertSModsToRegular: Boolean = false,
                useEffectiveHull: Boolean = false,
                compareTemporaryTags: Boolean = false,
            ) = CompareOptions(
                flux = flux, weapons = weapons, weaponGroups = weaponGroups, wings = wings, hullMods = hullMods, builtInHullMods = builtInHullMods, hiddenHullMods = hiddenHullMods,
                dMods = dMods, sMods = sMods, permaMods = permaMods, modules = modules, tags = tags, hull = hull, convertSModsToRegular = convertSModsToRegular, useEffectiveHull = useEffectiveHull,
                compareTemporaryTags = compareTemporaryTags
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

                // Safely get slots lists or empty lists. Exclude slots without weapons
                val slots1 = g1?.slots.orEmpty()
                    .filter { variant1.getWeaponId(it) != null }

                val slots2 = g2?.slots.orEmpty()
                    .filter { variant2.getWeaponId(it) != null }

                // both empty? skip
                if (slots1.isEmpty() && slots2.isEmpty()) return@forEachIndexed

                // property checks
                if (g1?.type != g2?.type) return false
                if (g1?.isAutofireOnByDefault != g2?.isAutofireOnByDefault) return false

                // mismatch in slots
                if (!slotsMatch(slots1, slots2)) return false
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
            if (!options.compareTemporaryTags) {
                variant1?.tags?.toList()?.forEach { if (it.startsWith("#")) variant1.removeTag(it) }
                variant2?.tags?.toList()?.forEach { if (it.startsWith("#")) variant2.removeTag(it) }
            }
            if (variant1.tags.size != variant2.tags.size) return false
            for (tag in variant1.tags) {
                if (!variant2.tags.contains(tag))
                    return false
            }
        }

        if (options.modules) {
            variant1.moduleSlots.forEach { slot ->
                val moduleVariant1 = runCatching { variant1.getModuleVariant(slot) }.getOrNull()
                val moduleVariant2 = runCatching { variant2.getModuleVariant(slot) }.getOrNull()
                when {
                    moduleVariant1 == null && moduleVariant2 == null -> {
                        // Both null.
                    }

                    moduleVariant1 == null || moduleVariant2 == null -> {
                        return false // One null, the other isn't
                    }

                    !compareVariantContents(moduleVariant1, moduleVariant2, options) -> {
                        return false // Both non-null but different
                    }
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
        val allDMods = LookupUtil.getAllDMods()
        val allHiddenEverywhereMods = LookupUtil.getAllHiddenEverywhereMods()

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

        if (!options.builtInHullMods) {
            val toRemove1 = variant1.hullSpec.builtInMods.filter { it !in allDMods }
            val toRemove2 = variant2.hullSpec.builtInMods.filter { it !in allDMods }

            toRemove1.forEach {
                variant1.completelyRemoveMod(it)
            }

            toRemove2.forEach {
                variant2.completelyRemoveMod(it)
            }
        } else {
            if (!hullModSetsEqual(variant1.hullSpec.builtInMods.toSet(), variant2.hullSpec.builtInMods.toSet()))
                return false
        }

        if (!options.sMods || options.convertSModsToRegular) {
            processSModsForComparison(variant1, options.convertSModsToRegular)
            processSModsForComparison(variant2, options.convertSModsToRegular)
        }

        if (!options.permaMods) {
            variant1.permaMods.toList().forEach {
                variant1.completelyRemoveMod(it)
            }
            variant2.permaMods.toList().forEach {
                variant2.completelyRemoveMod(it)
            }
        }

        val variantModsEqual = hullModSetsEqual(variant1.getRegularHullMods(), variant2.getRegularHullMods()) &&
                hullModSetsEqual(variant1.sModdedBuiltIns, variant2.sModdedBuiltIns) &&
                hullModSetsEqual(variant1.sMods, variant2.sMods) &&
                hullModSetsEqual(variant1.permaMods, variant2.permaMods) &&
                hullModSetsEqual(variant1.suppressedMods, variant2.suppressedMods)

        return variantModsEqual
    }

    internal fun processSModsForComparison(variant: ShipVariantAPI, convert: Boolean) {
        val sModsCopy = (variant.sMods + variant.sModdedBuiltIns).toSet()
        sModsCopy.forEach { sMod ->
            val isBuiltIn = sMod in variant.hullSpec.builtInMods
            variant.completelyRemoveMod(sMod)
            if (convert && !isBuiltIn) {
                variant.addMod(sMod)
            }
        }
    }
}