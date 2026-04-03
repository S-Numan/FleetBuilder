package fleetBuilder.util.kotlin

import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.util.api.HullUtils
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

fun Float.roundToDecimals(decimals: Int): Float {
    val factor = 10.0.pow(decimals).toFloat()
    return (this * factor).roundToInt() / factor
}

fun Double.roundToDecimals(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return round(this * factor) / factor
}

val String.toBinary: Int?
    get() =
        if (this.equals("TRUE", ignoreCase = true) || this == "1") 1
        else if (this.equals("FALSE", ignoreCase = true) || this == "0") 0
        else null

val String.toBoolean: Boolean?
    get() =
        when (this.toBinary) {
            1 -> true
            0 -> false
            else -> null
        }


fun String.isJSON(): Boolean {
    this.lineSequence()
        .map { it.substringBefore("#") }           // Remove inline comments
        .map { it.trim() }                          // Trim whitespace
        .filter { it.isNotEmpty() }                 // Ignore empty lines
        .forEach { line ->
            if (line.isNotEmpty()) {
                return line.first() == '{'
            }
        }
    return false
}

/**
 * Delegates to [HullUtils.createHullVariant].
 */
fun SettingsAPI.createHullVariant(hull: ShipHullSpecAPI): ShipVariantAPI =
    HullUtils.createHullVariant(hull)