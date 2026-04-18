package fleetBuilder.util.api.kotlin

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.loading.WeaponSpecAPI
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.LookupUtils.getAllDMods
import fleetBuilder.util.api.VariantUtils

/**
 * Creates a copy of this variant with the specified settings.
 */
fun ShipVariantAPI.clone(settings: VariantSettings): ShipVariantAPI {
    return DataVariant.cloneVariant(this, settings = settings)
}

/**
 * Returns a map of all modules attached to this variant.
 *
 * Any slots with null variants are filtered out. Use [getModulesAllowNull] if you want to include null variants.
 *
 * The map key is the module slot ID, and the value is the corresponding
 * [ShipVariantAPI] for that module.
 */
fun ShipVariantAPI.getModules(onlyThoseInHullSpec: Boolean = false): Map<String, ShipVariantAPI> {
    return getModulesAllowNull(onlyThoseInHullSpec)
        .mapNotNull { (k, v) -> v?.let { k to it } }
        .toMap()
}

fun ShipVariantAPI.getModulesAllowNull(onlyThoseInHullSpec: Boolean = false): Map<String, ShipVariantAPI?> {
    val modules = stationModules
        ?.map { (slot, _) ->
            val variant: ShipVariantAPI? = getModuleVariant(slot)
            slot to variant
        }
        ?.toMap() // converts the list of pairs back into a Map
        ?: emptyMap()

    return if (onlyThoseInHullSpec)
        modules.filter { hullSpec.getWeaponSlot(it.key)?.weaponType == WeaponAPI.WeaponType.STATION_MODULE }
    else
        modules
}
// Avoid using ShipVariantAPI.getModuleSlots(). It uses ShipVariantAPI.getStationModules() internally anyway.
// Only thing getModuleSlots checks differently is checking if the hullspec has the slot for the module
//Y var4 = this.getHullSpec().getWeaponSlot(var2);
//if (var4.getWeaponType() == WeaponType.STATION_MODULE

fun ShipVariantAPI.getSlotsForModules(onlyThoseInHullSpec: Boolean = false): Set<String> {
    val slots = stationModules?.keys ?: emptySet()

    return if (onlyThoseInHullSpec)
        slots.filter { hullSpec.getWeaponSlot(it).weaponType == WeaponAPI.WeaponType.STATION_MODULE }.toSet()
    else
        slots
}

/**
 * Returns all fitted weapons on this variant.
 *
 * This includes built in weapons, it does not include decorative weapons.
 *
 * The map key is the weapon slot ID, and the value is the [WeaponSpecAPI]
 * of the weapon installed in that slot.
 */
fun ShipVariantAPI.getFittedWeapons(): Map<String, WeaponSpecAPI> {
    val weapons = mutableMapOf<String, WeaponSpecAPI>()
    fittedWeaponSlots.forEach { slot ->
        val weapon = getWeaponSpec(slot) ?: return@forEach
        weapons[slot] = weapon
    }
    return weapons
}

/**
 * Returns all non-built-in weapons on this variant.
 *
 * This only excludes built in weapons
 *
 * The map key is the weapon slot ID, and the value is the [WeaponSpecAPI]
 * of the weapon installed in that slot.
 */
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
