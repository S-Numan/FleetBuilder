package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import fleetBuilder.core.FBConst
import fleetBuilder.serialization.MissingContent
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.api.VariantUtils.isVariantKnownToPlayer
import fleetBuilder.util.api.kotlin.*
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
     * @param settings the settings to use when checking for source mods
     * @return a set of all source mods from the variant
     */
    fun getAllSourceModsFromVariant(
        variant: ShipVariantAPI,
        settings: VariantSettings
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
        LookupUtils.getHullSpec(data.hullId)?.let { hullSpec ->
            hullSpec.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        // HullMods
        for (mod in data.hullMods) {
            LookupUtils.getHullModSpec(mod)?.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        // Weapons
        for (group in data.weaponGroups) {
            group.weapons.forEach { (slot, weaponId) ->
                LookupUtils.getWeaponSpec(weaponId)?.sourceMod?.let { sm ->
                    sourceMods.add(sm)
                }
            }
        }

        // Fighter Wings
        for (wing in data.wings) {
            LookupUtils.getFighterWingSpec(wing)?.sourceMod?.let { sm ->
                sourceMods.add(sm)
            }
        }

        return sourceMods
    }

    /**
     * Returns the full bonus XP that would be gained if this hullmod were to be built in to this variant as an sModdedBuiltIn.
     * If the hullmod is built into the hullspec, returns the default bonus XP.
     * If the hullmod is not built into the hullspec, returns the bonus XP for that hullmod.
     *
     * @param variant The ShipVariantAPI to check.
     * @param modID The ID of the hullmod to check.
     * @return The bonus XP that would be gained if this hullmod were to be added to this variant.
     */
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

    /**
     * Checks if a variant is known to the player. This function is intentionally very restrained and only lightly checks the variant.
     *
     * Includes the variant's hullspec, all non built in weapons, non built in fighter wings, and non built in hullmods. Includes modules, but does not check the module's hullspec.
     *
     * Weapons, wings, and hullmods are checked for if the CODEX_UNLOCKABLE tag is true, and if the player is not aware of it.
     * HIDE_IN_CODEX and other tags are not considered to avoid being overzealous with this check.
     *
     * @param variant The variant to check.
     * @return True if all components of the variant are known to the player, false otherwise.
     */
    fun isVariantKnownToPlayer(variant: ShipVariantAPI): Boolean {
        //  If module, replace variant with parent variant. Modules are considered known if their parent is known.
        //  This would need a function to get the parent variant of a module variant ... That isn't easily possible.

        val missing = MissingContent()
        whatVariantContentsAreNotKnownToPlayer(variant, missing)
        if (missing.hasMissing())
            return false
        else
            return true
    }

    /**
     * Returns every element the player is not aware of in a variant in a [MissingContent].
     *
     * See [isVariantKnownToPlayer] for more details. That function calls this one.
     */
    fun whatVariantContentsAreNotKnownToPlayer(variant: ShipVariantAPI, missing: MissingContent) {
        if (!HullUtils.isHullKnownToPlayer(variant.hullSpec))
            missing.hullIds.add(variant.hullSpec.hullId)

        fun addMissingContents(va: ShipVariantAPI) {
            va.getNonBuiltInWeapons().forEach { (_, weapon) ->
                if (weapon.hasTag(Tags.CODEX_UNLOCKABLE) && !SharedUnlockData.get().isPlayerAwareOfWeapon(weapon.weaponId))
                    missing.weaponIds.add(weapon.weaponId)
            }
            va.nonBuiltInWings.forEach { wing ->
                if (LookupUtils.getFighterWingSpec(wing)?.hasTag(Tags.CODEX_UNLOCKABLE) == true && !SharedUnlockData.get().isPlayerAwareOfFighter(wing))
                    missing.wingIds.add(wing)
            }
            va.nonBuiltInHullmods.forEach { mod ->
                if (va.hullSpec.isBuiltInMod(mod))
                    return@forEach

                if (LookupUtils.getHullModSpec(mod)?.hasTag(Tags.CODEX_UNLOCKABLE) == true && !SharedUnlockData.get().isPlayerAwareOfHullmod(mod))
                    missing.hullModIds.add(mod)
            }
        }

        addMissingContents(variant)

        variant.getModules().forEach { (_, moduleVariant) ->
            addMissingContents(moduleVariant)
        }
    }

    /**
     * Generates a variant ID based on the hull ID and the variant's display name.
     *
     * @param variant The variant to generate the ID for.
     * @return The generated variant ID.
     */
    fun makeVariantID(variant: ShipVariantAPI): String {
        val hullId = variant.hullSpec.getCompatibleDLessHullId(true)
        return makeVariantID(hullId, variant.displayName)
    }

    /**
     * Generates a variant ID based on a hull ID and a display name.
     *
     * @param hullId The ID of the hull.
     * @param displayName The display name of the variant.
     * @return The generated variant ID.
     */
    fun makeVariantID(hullId: String, displayName: String): String {
        val cleanName = displayName
            .replace(" ", "_")                 // replace spaces with underscores
            .replace(Regex("[^A-Za-z0-9_-]"), "")   // remove anything not a-z, A-Z, 0-9, dash, or underscore
            .trim('.')                        // remove leading/trailing dots
            .trim()                           // remove leading/trailing whitespace

        return "${hullId}_$cleanName"
    }

    //fun isErrorVariant(variant: ShipVariantAPI): Boolean {
    //    return variant.hasTag(errorTag)
    //}
    /**
     * Creates an error variant and marks it as so.
     *
     * Tags the variant to keep track that it is an error variant, and optionally changes the display name for the player to see why the error variant occurred.
     *
     * This function exists mostly for convenience.
     */
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

        tempVariant.addTag(FBConst.FB_ERROR_TAG)

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
            variant1.getModules().forEach { (slot, moduleVariant1) ->
                val moduleVariant2 = runCatching { variant2.getModuleVariant(slot) }.getOrNull()
                when {
                    moduleVariant2 == null -> {
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
        val allDMods = LookupUtils.getAllDMods()
        val allHiddenEverywhereMods = LookupUtils.getAllHiddenEverywhereMods()

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