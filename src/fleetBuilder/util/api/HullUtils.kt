package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.loading.VariantSource
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.getCompatibleDLessHullId
import fleetBuilder.util.getEffectiveHullId

object HullUtils {

    fun isHullKnownToPlayer(hull: ShipHullSpecAPI): Boolean {
        if (hull.hasTag(Tags.CODEX_UNLOCKABLE)) {
            if (!SharedUnlockData.get().isPlayerAwareOfShip(hull.hullId))
                return false
        } //else if (hull.hints.contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX) || hull.hasTag(Tags.HIDE_IN_CODEX))// || hull.hasTag(Tags.RESTRICTED))
        //return false

        return true
    }

    /**
     * Creates a ShipVariantAPI for a given ShipHullSpecAPI.
     *
     * This function exists because createEmptyVariant does not create modules.
     * Remember to change the source of the variant to VariantSource.REFIT if you don't want the variant to be forgotten between save games.
     *
     * @param hull The hull spec for which to create a variant
     *
     * @return A variant for the given hull spec.
     */
    fun createHullVariant(hull: ShipHullSpecAPI): ShipVariantAPI {
        return run {
            val variants = LookupUtils.getVariantsForEffectiveHullSpec(hull)

            variants.filter { it.source == VariantSource.HULL } // Filter out non hull variants
                .takeIf { it.isNotEmpty() }
                ?.let { hullVariants ->
                    hullVariants.find { it.hullSpec.hullId == hull.hullId }                             // Exact match
                        ?: hullVariants.find { it.hullSpec.hullId == hull.getCompatibleDLessHullId() }  // D-less match
                        ?: hullVariants.find { it.hullSpec.hullId == hull.getEffectiveHullId() }        // Effective match
                        ?: run {
                            Global.getLogger(javaClass).warn("Could not find ideal match when getting Hull Variant with hullId '${hull.hullId}' and effectiveId '${hull.getEffectiveHullId()}'")
                            hullVariants.firstOrNull()// Cannot find a good enough match, just go for whatever
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
     * - A base hull
     * - A D-modded hull (d-hull)
     * - A skin/variant derived from another hull
     *
     * This function resolves the hull to the most appropriate "base-like" hull
     * when the hull is compatible with its base.
     *
     * @param hull The hull spec to resolve.
     * @return The effective hull spec.
     */
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
     * Important Starsector quirks (0.98a):
     * - Some ships (e.g. Dominator D) have custom D-mod skins and variants.
     *   These may have `isCompatibleWithBase == true` but `dParentHull == null`.
     * - Some fake D-hulls have no custom assets and simply reference a parent hull.
     *
     * @param hull The hull spec to resolve.
     * @param keepDModSkin If true, preserves D-hull skins when they differ visually.
     * @return A compatible non-D hull when possible.
     */
    @JvmOverloads
    fun getCompatibleDLessHull(
        hull: ShipHullSpecAPI,
        keepDModSkin: Boolean = false
    ): ShipHullSpecAPI {

        if (!hull.isDHull) return hull

        if (keepDModSkin && (hull.baseHull != null && hull.baseHull.spriteName != hull.spriteName))
            return hull

        if (hull.isCompatibleWithBase) {
            if (hull.dParentHull != null)
                return hull.dParentHull
            else if (!keepDModSkin && hull.baseHull != null)
                return hull.baseHull
        }

        return hull
    }
}