package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.CargoStackAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.impl.items.ModSpecItemPlugin
import com.fs.starfarer.api.campaign.impl.items.MultiBlueprintItemPlugin
import com.fs.starfarer.api.campaign.impl.items.ShipBlueprintItemPlugin
import com.fs.starfarer.api.campaign.impl.items.WeaponBlueprintItemPlugin
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.variants.VariantLib
import fleetBuilder.variants.VariantLib.getAllDMods
import org.json.JSONArray
import org.json.JSONObject
import starficz.ReflectionUtils.getMethodsMatching
import starficz.getChildrenCopy
import kotlin.math.pow
import kotlin.math.roundToInt

fun Float.roundToDecimals(decimals: Int): Float {
    val factor = 10.0.pow(decimals).toFloat()
    return (this * factor).roundToInt() / factor
}

fun JSONArray.containsString(value: String): Boolean {
    for (i in 0 until this.length()) {
        if (this.optString(i) == value) return true
    }
    return false
}

fun JSONObject.optJSONArrayToStringList(fieldName: String): List<String> {
    val array = optJSONArray(fieldName) ?: return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
        val value = array.optString(i, null)
        if (value != null) {
            list.add(value)
        } else {
            Global.getLogger(this.javaClass).warn("Invalid string at index $i in '$fieldName'")
        }
    }
    return list
}

// Extension to get the effective hull
fun ShipHullSpecAPI.getEffectiveHull(): ShipHullSpecAPI {
    return if (isCompatibleWithBase) {//If the hull is mostly compatible from an loadout perspective with a
        if (dParentHull != null) {
            if (dParentHull.isCompatibleWithBase)
                dParentHull.baseHull ?: dParentHull
            else
                dParentHull
        } else baseHull ?: this
    } else
        this
}

fun ShipHullSpecAPI.getEffectiveHullId(): String {
    return this.getEffectiveHull().hullId
}

fun ShipHullSpecAPI.getCompatibleDLessHull(): ShipHullSpecAPI {
    return if (isCompatibleWithBase && dParentHull != null)
        dParentHull
    else
        this
}

fun ShipHullSpecAPI.getCompatibleDLessHullId(): String {
    return this.getCompatibleDLessHull().hullId
}

fun ShipVariantAPI.completelyRemoveMod(modId: String) {
    sModdedBuiltIns.remove(modId)
    suppressedMods.remove(modId)
    if (!hullSpec.builtInMods.contains(modId))
        hullMods.remove(modId)
    removePermaMod(modId)
}

fun ShipVariantAPI.isEquivalentTo(
    other: ShipVariantAPI,
    options: VariantLib.CompareOptions = VariantLib.CompareOptions()
): Boolean {
    return VariantLib.compareVariantContents(
        this,
        other,
        options
    )
}

fun ShipVariantAPI.allDMods(): Set<String> {
    val allDMods = getAllDMods()
    val dMods = mutableSetOf<String>()
    for (mod in hullMods) {
        if (mod in allDMods)
            dMods.add(mod)
    }
    return dMods
}

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

//Gets all hullmods that are not smods, perma mods, suppressed mods, or built in mods. Just ordinary hullmods.
fun ShipVariantAPI.getRegularHullMods(): Set<String> {
    return hullMods
        .filter { !sModdedBuiltIns.contains(it) && !sMods.contains(it) && !permaMods.contains(it) && !suppressedMods.contains(it) && !hullSpec.builtInMods.contains(it) }
        .toSet()
}

fun FleetMemberAPI.getShipNameWithoutPrefix(): String {
    val fullName = shipName ?: return ""
    val knownPrefixes = buildSet {
        add("ISS") // Default

        // Add from current fleetData (if any)
        fleetData?.fleet?.faction?.shipNamePrefix?.let { if (it.isNotBlank()) add(it) }
        fleetData?.fleet?.faction?.shipNamePrefixOverride?.let { if (it.isNotBlank()) add(it) }

        // Loop through all known factions
        Global.getSector().allFactions.forEach { faction ->
            faction?.shipNamePrefix?.let { if (it.isNotBlank()) add(it) }
            faction?.shipNamePrefixOverride?.let { if (it.isNotBlank()) add(it) }
        }
    }.toSet()

    val parts = fullName.trim().split("\\s+".toRegex())
    return if (parts.size > 1 && knownPrefixes.contains(parts[0])) {
        parts.drop(1).joinToString(" ")
    } else {
        fullName
    }
}

fun CampaignUIAPI.getActualCurrentTab(): CoreUITabId? {
    if (!Global.getSector().isPaused) return null
    if (currentInteractionDialog != null && currentInteractionDialog.interactionTarget != null) {
        //Validate that we're not stuck in a ghost interaction dialog. (Happens when you escape out of a UI screen while in an interaction dialog. It reports that the player is still in that ui screen, which is false)
        if (currentInteractionDialog.optionPanel != null && currentInteractionDialog.optionPanel.savedOptionList.isNotEmpty()) return null
    }

    return currentCoreTab
}

val String.toBinary: Int
    get() = if (this.equals("TRUE", ignoreCase = true)) 1 else 0

fun CargoStackAPI.moveStack(to: CargoAPI, inputAmount: Float = -1f) {
    if (!this.isNull && this.cargo !== to && inputAmount != 0f) {
        val moveAmount = minOf(if (inputAmount == -1f) this.size else inputAmount, this.size)
        if (moveAmount > 0) {
            to.addItems(this.type, this.data, moveAmount)
            this.cargo.removeItems(this.type, this.data, moveAmount)
        }
    }
}

fun CargoAPI.moveItem(
    type: CargoAPI.CargoItemType,
    data: Any?,
    to: CargoAPI,
    inputAmount: Float = -1f
) {
    var remaining = inputAmount

    for (stack in this.stacksCopy) {
        if (stack.type != type || stack.data != data) continue

        val stackSize = stack.size
        val moveAmount = when {
            inputAmount == -1f -> stackSize // move entire stack
            remaining <= 0f -> break
            else -> minOf(stackSize, remaining)
        }

        stack.moveStack(to, moveAmount)

        if (inputAmount != -1f) {
            remaining -= moveAmount
        }
    }
}

fun CargoAPI.getWeaponAndWingQuantity(): Float {
    var count = 0f
    for (stack in this.stacksCopy) {
        if (stack.isNull) continue

        if (stack.type == CargoAPI.CargoItemType.WEAPONS || stack.type == CargoAPI.CargoItemType.FIGHTER_CHIP) {
            count += stack.size
        }
    }
    return count
}

fun CargoAPI.moveWeaponAndWings(to: CargoAPI, inputAmount: Float = -1f) {
    var remaining = inputAmount

    for (stack in this.stacksCopy) {
        if (stack.isNull) continue

        if (stack.type == CargoAPI.CargoItemType.WEAPONS || stack.type == CargoAPI.CargoItemType.FIGHTER_CHIP) {
            val stackSize = stack.size
            val moveAmount = minOf(stackSize, remaining)

            stack.moveStack(to, moveAmount)

            if (inputAmount != -1f) {
                remaining -= moveAmount

                if (remaining <= 0f)
                    break
            }
        }
    }
}

fun CargoAPI.getBlueprintAndModSpecQuantity(): Float {
    var count = 0f
    for (stack in this.stacksCopy) {
        if (stack.isNull) continue
        if (stack.isBlueprintOrModSpec()) {
            count += stack.size
        }
    }
    return count
}

fun CargoAPI.moveBlueprintAndModSpec(to: CargoAPI, inputAmount: Float = -1f) {
    var remaining = inputAmount

    for (stack in this.stacksCopy) {
        if (stack.isNull) continue

        if (stack.isBlueprintOrModSpec()) {
            val stackSize = stack.size
            val moveAmount = minOf(stackSize, remaining)

            stack.moveStack(to, moveAmount)

            if (inputAmount != -1f) {
                remaining -= moveAmount

                if (remaining <= 0f)
                    break
            }
        }
    }
}

fun CargoStackAPI.isBlueprintOrModSpec(): Boolean {
    return this.type == CargoAPI.CargoItemType.SPECIAL && (this.plugin is ShipBlueprintItemPlugin || this.plugin is WeaponBlueprintItemPlugin || this.plugin is MultiBlueprintItemPlugin || this.plugin is ModSpecItemPlugin)
}

fun String.startsWithJsonBracket(): Boolean {
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


//For optimization purposes
internal fun UIPanelAPI.findChildWithMethodReversed(methodName: String): UIComponentAPI? {
    return getChildrenCopy().asReversed().find { it.getMethodsMatching(name = methodName).isNotEmpty() }
}

//internal fun UIPanelAPI.findChildWithField(fieldName: String): UIComponentAPI? {
//    return getChildrenCopy().find { it.getFieldsMatching(name = fieldName).isNotEmpty() }
//}
/*
fun PersonAPI.isGenericOfficer(): Boolean {
    var hasSkill = false
    for (skill in stats.skillsCopy) {
        if(skill.level != 0f) {
            hasSkill = true
            break
        }
    }
    return !hasSkill
}*/