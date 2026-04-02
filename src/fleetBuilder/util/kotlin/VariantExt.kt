package fleetBuilder.util.kotlin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.loading.WeaponSpecAPI
import fleetBuilder.util.LookupUtils.getAllDMods
import fleetBuilder.util.api.VariantUtils


// Avoid using getModuleSlots(). It uses getStationModules() internally anyway.
fun ShipVariantAPI.getModules(): Map<String, ShipVariantAPI> {
    return this.stationModules.mapValues { getModuleVariant(it.key) }
}

fun ShipVariantAPI.getFittedWeapons(): Map<String, WeaponSpecAPI> {
    val weapons = mutableMapOf<String, WeaponSpecAPI>()
    fittedWeaponSlots.forEach { slot ->
        val weapon = getWeaponSpec(slot) ?: return@forEach
        weapons[slot] = weapon
    }
    return weapons
}

fun ShipVariantAPI.getNonBuiltInWeapons(): Map<String, WeaponSpecAPI> {
    val weapons = mutableMapOf<String, WeaponSpecAPI>()
    nonBuiltInWeaponSlots.forEach { slot ->
        val weapon = getWeaponSpec(slot) ?: return@forEach
        weapons[slot] = weapon
    }
    return weapons
}
//fun ShipVariantAPI.forEachNonBuiltInWeapon(action: (slot: String, weapon: WeaponSpecAPI) -> Unit) {


/**
 * Completely removes a mod from the variant. This includes removing it from sMods, sModdedBuiltIns, suppressedMods, and hullMods.
 *
 * @param modId The ID of the mod to be removed.
 */
fun ShipVariantAPI.completelyRemoveMod(modId: String, removeBuiltIns: Boolean = false) {
    sModdedBuiltIns.remove(modId)
    suppressedMods.remove(modId)
    if (!hullSpec.builtInMods.contains(modId) || removeBuiltIns)
        hullMods.remove(modId)
    removePermaMod(modId)
}

fun ShipVariantAPI.isEquivalentTo(
    other: ShipVariantAPI,
    options: VariantUtils.CompareOptions = VariantUtils.CompareOptions()
): Boolean {
    return VariantUtils.compareVariantContents(
        this,
        other,
        options
    )
}


/**
 * Returns a set of all DMods in the variant.
 */
fun ShipVariantAPI.allDMods(): Set<String> {
    val allDMods = getAllDMods()
    val dMods = mutableSetOf<String>()
    for (mod in hullMods) {
        if (mod in allDMods)
            dMods.add(mod)
    }
    return dMods
}

/**
 * Returns a set of all SMods in the variant. This includes both SMods and SModdedBuiltIns.
 */
fun ShipVariantAPI.allSMods(): Set<String> {
    val outputSMods = mutableSetOf<String>()
    for (mod in sMods) {
        outputSMods.add(mod)
    }
    for (mod in sModdedBuiltIns) {
        outputSMods.add(mod)
    }
    return outputSMods
}

/**
 * Gets all hullmods that are not sMods, perma mods, suppressed mods, or built-in mods. Simply the ordinary hullmods only.
 */
fun ShipVariantAPI.getRegularHullMods(): Set<String> {
    return hullMods
        .filter { !sModdedBuiltIns.contains(it) && !sMods.contains(it) && !permaMods.contains(it) && !suppressedMods.contains(it) && !hullSpec.builtInMods.contains(it) }
        .toSet()
}


fun ShipVariantAPI.createFleetMember(): FleetMemberAPI =
    Global.getSettings().createFleetMember(if (isFighter) FleetMemberType.FIGHTER_WING else FleetMemberType.SHIP, this)
