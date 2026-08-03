package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.util.api.HullUtils

/**
 * Delegates to [HullUtils.isDHullFix]. See that function for details on why this exists.
 */
fun ShipHullSpecAPI.isDHullFix(): Boolean =
    HullUtils.isDHullFix(this)

/**
 * Delegates to [HullUtils.getCompatibleDLessHull].
 */
internal fun ShipHullSpecAPI.getCompatibleDLessHull(): ShipHullSpecAPI =
    HullUtils.getCompatibleDLessHull(this)

/**
 * Returns the compatible D less hull ID of this [ShipHullSpecAPI]. See [HullUtils.getCompatibleDLessHull].
 */
internal fun ShipHullSpecAPI.getCompatibleDLessHullId(): String =
    HullUtils.getCompatibleDLessHull(this).hullId

/*
/**
 * Returns the base hull if the hull is a D-Hull.
 *
 * Note that this does not consider if the non D-Hull is compatible with the current hull. If you want compatibility or are simply unsure of what you're doing, use [getCompatibleDLessHull] instead.
 *
 * Delegates to [HullUtils.getDLessHull].
 */
fun ShipHullSpecAPI.getDLessHull(): ShipHullSpecAPI =
    HullUtils.getDLessHull(this)
*/