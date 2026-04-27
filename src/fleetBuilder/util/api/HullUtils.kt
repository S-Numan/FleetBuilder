package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.loading.VariantSource
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.api.HullUtils.getCompatibleDLessHull
import fleetBuilder.util.api.HullUtils.isDHullFix
import fleetBuilder.util.api.kotlin.getActualHullId
import fleetBuilder.util.api.kotlin.getCompatibleDLessHullId
import fleetBuilder.util.api.kotlin.getEffectiveHullId

object HullUtils {

    /**
     * Returns a list of weapon slot IDs which are [WeaponAPI.WeaponType.STATION_MODULE].
     *
     * These are the points on the hull which modules are intended to attach too.
     *
     * @param hull The hull spec to query.
     * @return A list of weapon slot IDs that are station modules.
     */
    @JvmStatic
    fun getSlotsForModules(hull: ShipHullSpecAPI): List<String> {
        return hull.allWeaponSlotsCopy.mapNotNull {
            if (it.weaponType == WeaponAPI.WeaponType.STATION_MODULE)
                it.id
            else
                null
        }
    }

    /**
     * Checks if a hull is known to the player.
     *
     * If the hull is tagged with [Tags.CODEX_UNLOCKABLE] and the player is not aware of it, the function returns false.
     * Otherwise, it returns true.
     *
     * @param hull The hull to check.
     * @return True if the hull is known to the player, false otherwise.
     */
    @JvmStatic
    fun isHullKnownToPlayer(hull: ShipHullSpecAPI): Boolean {
        if (hull.hasTag(Tags.CODEX_UNLOCKABLE)) {
            if (!SharedUnlockData.get().isPlayerAwareOfShip(hull.hullId))
                return false
        }

        return true
    }

    /**
     * Creates a ShipVariantAPI for a given ShipHullSpecAPI.
     *
     * Remember to change the source of the variant to VariantSource.REFIT if you don't want the variant to be forgotten between save games.
     *
     * This function exists because createEmptyVariant does not create modules.
     *
     * @param hull The hull spec for which to create a variant
     * @return A variant for the given hull spec.
     */
    @JvmStatic
    fun createHullVariant(hull: ShipHullSpecAPI): ShipVariantAPI {
        return run {
            val variants = LookupUtils.getVariantsForEffectiveHullSpec(hull)

            variants.filter { it.source == VariantSource.HULL } // Filter out non hull variants
                .takeIf { it.isNotEmpty() }
                ?.let { hullVariants ->
                    hullVariants.find { it.hullSpec.hullId == hull.hullId }                             // Exact match
                        ?: hullVariants.find { it.hullSpec.hullId == hull.getActualHullId() }           // Actual match
                        ?: hullVariants.find { it.hullSpec.hullId == hull.getCompatibleDLessHullId() }  // D-less match
                        ?: hullVariants.find { it.hullSpec.hullId == hull.getEffectiveHullId() }        // Effective match
                        ?: run {
                            Global.getLogger(javaClass).warn("Could not find ideal match when getting Hull Variant with hullId '${hull.hullId}' and effectiveId '${hull.getEffectiveHullId()}'")
                            hullVariants.firstOrNull() // Cannot find a good enough match, just go for whatever
                        }
                }
        } ?: runCatching {
            val emptyVariant = Global.getSettings().createEmptyVariant(hull.hullId, hull)
            Global.getLogger(javaClass).warn(
                "Failed to find HULL variant for '${hull.hullId}' and fell back to createEmptyVariant. This can usually be ignored." +
                        "\nHowever, ships may spawn without modules which can crash the game in certain circumstances"
            )
            emptyVariant
        }.getOrNull() ?: run {
            DisplayMessage.showError("Failed to find HULL variant for '${hull.hullId}'")
            VariantUtils.createErrorVariant("MISSINGHULLVARIANT:${hull.hullId}")
        }
    }

    /**
     * Returns the "effective" hull for a hull spec.
     *
     * In Starsector, hull specs may represent:
     * - A base hull (Normal ship hull made straight from the .ship file)
     * - A skin/variant derived from another hull (Normal ship skin made straight from the .skin file)
     * - A D-Modded hull skin (Ship skin made straight from the .skin file. Notably has DMods as built in mods, sometimes missing mounts and a restoreToBaseHull)
     * - A default D-Hull (Ship Hull/Skin with _D placed at the end to be annoying. Has no seemingly other meaningful changes aside from ruining hullID comparisons)
     *
     * This function resolves the hull to the most appropriate "base-like" hull
     * when the hull is compatible with its base.
     *
     * @param hull The hull spec to resolve.
     * @return The effective hull spec.
     */
    @JvmStatic
    fun getEffectiveHull(hull: ShipHullSpecAPI): ShipHullSpecAPI {
        return if (hull.isCompatibleWithBase) {
            if (hull.dParentHull != null) {
                val dParent = hull.dParentHull
                if (dParent.isCompatibleWithBase)
                    dParent.baseHull ?: dParent
                else
                    dParent
            } else hull.baseHull ?: hull
        } else {
            hull
        }
    }

    /**
     * Returns a hull compatible with the base hull but without D-mods when possible.
     *
     * This attempts to resolve a D-hull to its non-D equivalent while respecting
     * Starsector's special cases.
     *
     * @param hull The hull spec to resolve.
     * @return A compatible non-D hull when possible.
     */
    @JvmStatic
    fun getCompatibleDLessHull(
        hull: ShipHullSpecAPI,
    ): ShipHullSpecAPI {
        if (!hull.isCompatibleWithBase) return hull
        if (!hull.isDefaultDHull && !isDSkin(hull)) return hull
        return getDLessHull(hull)
    }

    /**
     * Returns the HullSpec from its source file (.ship or .skin), without any extra modifications such as default D-Hull variations.
     *
     * @param hull The hull spec to resolve.
     * @return The actual hull spec.
     */
    @JvmStatic
    fun getActualHull(
        hull: ShipHullSpecAPI
    ): ShipHullSpecAPI {
        if (!hull.isCompatibleWithBase) return hull
        if (isSkin(hull)) return hull
        return hull.dParentHull ?: hull
    }

    /**
     * Returns the base hull if the hull is a D-Hull.
     *
     * Note that this does not consider if the non D-Hull is compatible with the current hull. If you want compatibility or are simply unsure of what you're doing, use [getCompatibleDLessHull] instead.
     *
     * @param hull The hull spec to resolve.
     * @return A non D-Hull when possible. If the input hull is not a D-Hull, it is returned unchanged.
     */
    @JvmStatic
    fun getDLessHull(hull: ShipHullSpecAPI): ShipHullSpecAPI {
        if (!isDHullFix(hull)) return hull

        return if (hull.dParentHull != null) {
            val dParent = hull.dParentHull
            if (isDHullFix(dParent) && dParent.isCompatibleWithBase)
                dParent.baseHull ?: dParent
            else
                dParent
        } else
            hull
    }

    /**
     * Vanilla isDHull considers any hull with built-in D-Mods to be a D-Hull. This makes lion guard ships D-Hulls.
     *
     * I consider that incorrect behavior. Additional behavior added: If the D-Hull is a skin, it must have the isRestoreToBase value set to true as D-Mod skins typically have that set as true.
     */
    @JvmStatic
    fun isDHullFix(hull: ShipHullSpecAPI): Boolean {
        if (hull.isDefaultDHull) return true
        return isDSkin(hull)
    }

    /**
     * A D-skin is a hull skin that has built-in D-Mods and has the isRestoreToBase value set to true.
     *
     * This explicitly only returns true if this hull is a skin. It does not consider D-Hulls with default D-Mods. Please use [isDHullFix] to check for all types of DHulls.
     *
     * @param hull The hull to check.
     * @return True if the hull is a D-Skin, false otherwise.
     */
    // Marked as private to avoid confusion
    private fun isDSkin(hull: ShipHullSpecAPI): Boolean {
        return isSkin(hull) && hull.builtInMods.any { LookupUtils.getHullModSpec(it)?.hasTag(Tags.HULLMOD_DMOD) == true } // Has DMod as built in mod
                && hull.isRestoreToBase // And is restorable
    }

    /**
     * A skin is a hull that is a variation of another hull. Skins are made from .skin files.
     *
     * @param hull The hull to check.
     * @return True if the hull is a skin, false otherwise.
     */
    @JvmStatic
    fun isSkin(hull: ShipHullSpecAPI): Boolean {
        val hull = hull.dParentHull ?: hull
        return hull.baseHullId != hull.hullId
    }
}