package fleetBuilder.util.kotlin

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
 * Delegates to [HullUtils.createHullVariant].
 */
fun ShipHullSpecAPI.createHullVariant(): ShipVariantAPI =
    HullUtils.createHullVariant(this)