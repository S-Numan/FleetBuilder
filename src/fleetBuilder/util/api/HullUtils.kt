package fleetBuilder.util.api

import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.impl.SharedUnlockData
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.util.Misc
import org.magiclib.util.MagicLookup
import org.magiclib.util.api.getActualHull
import org.magiclib.util.api.isSkin

object HullUtils {

    /**
     * Checks if a hull is known to the player.
     *
     * If the hull is tagged with [Tags.CODEX_UNLOCKABLE] and the player is not aware of it, the function returns false.
     * Otherwise, it returns true.
     *
     * @param hull The hull to check.
     * @return True if the hull is known to the player, false otherwise.
     */
    @JvmOverloads
    @JvmStatic
    fun isHullKnownToPlayer(hull: ShipHullSpecAPI, checkHideInCodex: Boolean = false): Boolean {
        if (hull.hasTag(Tags.CODEX_UNLOCKABLE)) {
            if (!SharedUnlockData.get().isPlayerAwareOfShip(hull.hullId))
                return false
        } else if (checkHideInCodex && (hull.hasTag(Tags.HIDE_IN_CODEX) || hull.hints.contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX)))
            return false

        return true
    }

    /**
     * For use with hullID strings only. If you have the [ShipHullSpecAPI], use [getActualHull] instead.
     *
     * @return removes the _default_D hull suffix
     */
    @JvmStatic
    fun getActualHullID(
        hullID: String
    ): String {
        return hullID.removeSuffix(Misc.D_HULL_SUFFIX)
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
        val hull = hull.getActualHull()
        return hull.isSkin() && hull.builtInMods.any { MagicLookup.getHullModSpec(it)?.hasTag(Tags.HULLMOD_DMOD) == true } // Has DMod as built in mod
                && hull.isRestoreToBase // And is restorable
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
     * Returns a hull compatible with the base hull but without D-mods when possible.
     *
     * This attempts to resolve a D-hull to its non-D equivalent while respecting
     * Starsector's special cases.
     *
     * @param hull The hull spec to resolve.
     * @return A compatible non-D hull when possible.
     */
    // Marked internal to avoid needless functions and possible confusion from such
    internal fun getCompatibleDLessHull(
        hull: ShipHullSpecAPI,
    ): ShipHullSpecAPI {
        val hull = hull.getActualHull()
        if (!hull.isCompatibleWithBase) return hull
        if (!isDSkin(hull)) return hull
        return hull.dParentHull?.let { getCompatibleDLessHull(hull) } ?: hull.baseHull ?: hull
    }

    /*
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
    */
}