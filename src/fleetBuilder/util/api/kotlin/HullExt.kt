package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.util.api.HullUtils

/**
 * Delegates to [HullUtils.getEffectiveHull].
 */
fun ShipHullSpecAPI.getEffectiveHull(): ShipHullSpecAPI =
    HullUtils.getEffectiveHull(this)

/**
 * Returns the effective hull ID of this [ShipHullSpecAPI]. See [HullUtils.getEffectiveHull].
 */
fun ShipHullSpecAPI.getEffectiveHullId(): String =
    HullUtils.getEffectiveHull(this).hullId

/**
 * Delegates to [HullUtils.getCompatibleDLessHull].
 */
fun ShipHullSpecAPI.getCompatibleDLessHull(keepDModSkin: Boolean = false): ShipHullSpecAPI =
    HullUtils.getCompatibleDLessHull(this, keepDModSkin)

/**
 * Returns the compatible D less hull ID of this [ShipHullSpecAPI]. See [HullUtils.getCompatibleDLessHull].
 */
fun ShipHullSpecAPI.getCompatibleDLessHullId(keepDModSkin: Boolean = false): String =
    HullUtils.getCompatibleDLessHull(this, keepDModSkin).hullId

/**
 * Delegates to [HullUtils.isDHullFixed]. See that function for details on why this exists.
 */
fun ShipHullSpecAPI.isDHullFixed(): Boolean =
    HullUtils.isDHullFixed(this)

/**
 * Delegates to [HullUtils.isSkin].
 */
fun ShipHullSpecAPI.isSkin(): Boolean =
    HullUtils.isSkin(this)

/**
 * Returns the base hull if the hull is a D-Hull.
 *
 * Note that this does not consider if the non D-Hull is compatible with the current hull. If you want compatibility or are simply unsure of what you're doing, use [getCompatibleDLessHull] instead.
 *
 * Delegates to [HullUtils.getDLessHull].
 */
fun ShipHullSpecAPI.getDLessHull(): ShipHullSpecAPI =
    HullUtils.getDLessHull(this)

/**
 * Delegates to [HullUtils.createHullVariant].
 */
fun ShipHullSpecAPI.createHullVariant(): ShipVariantAPI =
    HullUtils.createHullVariant(this)