package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import fleetBuilder.util.api.HullUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

fun Float.roundToDecimals(decimals: Int): Float {
    if (this.isNaN()) return 0f
    val factor = 10.0.pow(decimals).toFloat()
    return (this * factor).roundToInt() / factor
}

fun Double.roundToDecimals(decimals: Int): Double {
    if (this.isNaN()) return 0.0
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


/*
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
 */

/**
 * Delegates to [HullUtils.createHullVariant].
 */
fun SettingsAPI.createHullVariant(hull: ShipHullSpecAPI): ShipVariantAPI =
    HullUtils.createHullVariant(hull)

internal inline fun <T> withoutLogging(block: () -> T): T {
    val rootLogger = Logger.getRootLogger()
    val previousLevel = rootLogger.level
    return try {
        rootLogger.level = Level.OFF
        block()
    } finally {
        rootLogger.level = previousLevel
    }
}

/**
 * Checks if a JSON file exists in `/data`.
 *
 * Optionally suppresses the usual "Loading JSON from ..." log output when attempting to load the file.
 *
 * Note: When [withoutLogging] is `true`, this temporarily modifies the global logger level and is therefore
 * not thread-safe. In rare cases, log messages from unrelated code running on separate threads may be suppressed during execution.
 * Avoid using [withoutLogging] on threads other than the main thread to minimize this issue.
 *
 * @param filename The filename of the JSON file.
 * @param modID The mod ID of the mod to check. If null, checks all available sources.
 * @param withoutLogging If `true`, suppresses logging while attempting to load the JSON.
 * @return `true` if the JSON file exists and can be loaded, `false` otherwise.
 */
fun SettingsAPI.doesJSONExist(
    filename: String,
    modID: String? = null,
    withoutLogging: Boolean = false
): Boolean {
    return try {
        if (withoutLogging) {
            withoutLogging {
                modID?.let { loadJSON(filename, it) } ?: loadJSON(filename)
            }
        } else {
            modID?.let { loadJSON(filename, it) } ?: loadJSON(filename)
        }
        true
    } catch (_: Exception) {
        false
    }
}