package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.impl.items.BlueprintProviderItem
import com.fs.starfarer.api.campaign.impl.items.ModSpecItemPlugin
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.ui.*
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.ui.common.ObservedTextField
import fleetBuilder.util.LookupUtil.getAllDMods
import fleetBuilder.util.api.FleetUtils
import fleetBuilder.util.api.HullUtils
import fleetBuilder.util.api.VariantUtils
import org.apache.log4j.Level
import org.json.JSONArray
import org.json.JSONObject
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.getChildrenCopy
import starficz.onClick
import java.awt.Color
import kotlin.math.pow
import kotlin.math.roundToInt

fun Float.roundToDecimals(decimals: Int): Float {
    val factor = 10.0.pow(decimals).toFloat()
    return (this * factor).roundToInt() / factor
}

fun Long.roundToDecimals(decimals: Int): Float {
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
            DisplayMessage.logMessage("Invalid string at index $i in '$fieldName'", Level.WARN, this.javaClass)
        }
    }
    return list
}

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
 * Completely removes a mod from the variant. This includes removing it from sMods, sModdedBuiltIns, suppressedMods, and hullMods.
 *
 * @param modId The ID of the mod to be removed.
 */
fun ShipVariantAPI.completelyRemoveMod(modId: String) {
    sModdedBuiltIns.remove(modId)
    suppressedMods.remove(modId)
    if (!hullSpec.builtInMods.contains(modId))
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

fun FleetMemberAPI.getShipNameWithoutPrefix(): String {
    val fullName = shipName ?: return ""
    val knownPrefixes = buildSet {
        add("ISS") // Default

        // Add from current fleetData (if any)
        fleetData?.fleet?.faction?.shipNamePrefix?.let { if (it.isNotBlank()) add(it) }
        fleetData?.fleet?.faction?.shipNamePrefixOverride?.let { if (it.isNotBlank()) add(it) }

        // Loop through all known factions
        Global.getSector()?.allFactions?.forEach { faction ->
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

/**
 * Returns the actual current tab of the campaign UI.
 *
 * This function is necessary because the campaign UI sometimes reports that the player is still in a UI screen even if they are not.
 * This can happen when the player escapes out of a UI screen while in an interaction dialog.
 *
 * This function checks if the player is in a ghost interaction dialog and if so, returns null, indicating that the player is not in a UI screen.
 */
fun CampaignUIAPI.getActualCurrentTab(): CoreUITabId? {
    if (!Global.getSector().isPaused) return null
    if (currentInteractionDialog != null && currentInteractionDialog.interactionTarget != null) {
        // Validate that we're not stuck in a ghost interaction dialog. (Happens when you escape out of a UI screen while in an interaction dialog. It reports that the player is still in that ui screen, which is false)
        if (currentInteractionDialog.optionPanel != null && currentInteractionDialog.optionPanel.savedOptionList.isNotEmpty()) return null
    }

    return currentCoreTab
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
    return this.type == CargoAPI.CargoItemType.SPECIAL && (this.plugin is BlueprintProviderItem || this.plugin is ModSpecItemPlugin)
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

// Why was this commented out? Does it have an issue?
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

fun CampaignUIAPI.isIdle(): Boolean {
    return currentInteractionDialog == null &&
            !isShowingDialog &&
            !isShowingMenu
}

/**
 * Delegates to [HullUtils.createHullVariant].
 */
fun SettingsAPI.createHullVariant(hull: ShipHullSpecAPI): ShipVariantAPI =
    HullUtils.createHullVariant(hull)

/**
 * Delegates to [HullUtils.createHullVariant].
 */
fun ShipHullSpecAPI.createHullVariant(): ShipVariantAPI =
    HullUtils.createHullVariant(this)

fun ShipVariantAPI.createFleetMember(): FleetMemberAPI =
    Global.getSettings().createFleetMember(if (isFighter) FleetMemberType.FIGHTER_WING else FleetMemberType.SHIP, this)


internal fun Any.safeInvoke(name: String? = null, vararg args: Any?): Any? {
    val paramTypes = args.map { arg -> arg?.let { it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
    val reflectedMethods = this.getMethodsMatching(name, parameterTypes = paramTypes)
    if (reflectedMethods.isEmpty()) {
        DisplayMessage.showError(
            short = "ERROR: No method found on class: ${this::class.java.name}. See console for more details.",
            full = "No method found for name: '$name' on class: ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    } else if (reflectedMethods.size > 1) {
        DisplayMessage.showError(
            short = "ERROR: Ambiguous method call on class: ${this::class.java.name}. See console for more details.",
            full = "Ambiguous method call for name: '$name' on class: ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}"
        )
    } else return reflectedMethods[0].invoke(this, *args)

    return null
}

internal fun Any.safeGet(name: String? = null, type: Class<*>? = null, searchSuperclass: Boolean = false): Any? {
    val reflectedFields = this.getFieldsMatching(name, fieldAssignableTo = type, searchSuperclass = searchSuperclass)
    if (reflectedFields.isEmpty())
        DisplayMessage.showError(
            short = "ERROR: No field found on class: ${this::class.java.name}. See console for more details.",
            full = "No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that is assignable to type: '${type?.name ?: "<any>"}'."
        )
    else if (reflectedFields.size > 1)
        DisplayMessage.showError(
            short = "ERROR: Ambiguous fields on class: ${this::class.java.name}. See console for more details.",
            full = "Ambiguous fields with name: '${name ?: "<any>"}' on class ${this::class.java.name} " +
                    "assignable to type: '${type?.name ?: "<any>"}'. Multiple fields match."
        )
    else return reflectedFields[0].get(this)

    return null
}

//For optimization purposes
internal fun UIPanelAPI.findChildWithMethodReversed(methodName: String): UIComponentAPI? {
    return getChildrenCopy().asReversed().find { it.getMethodsMatching(name = methodName).isNotEmpty() }
}

internal fun UIPanelAPI.findChildWithPlugin(clazz: Class<*>): CustomPanelAPI? {
    return getChildrenCopy().firstOrNull { child ->
        (child as? CustomPanelAPI)?.plugin?.let { clazz.isInstance(it) } == true
    } as? CustomPanelAPI
}

fun FleetDataAPI.getUnassignedOfficers(): List<PersonAPI> =
    FleetUtils.getUnassignedOfficers(this)

// Do not show hotkey on button
fun ButtonAPI.addShortcutNoShow(key: Int) {
    this.safeInvoke("addExtraShortcut", key, false, false, false)
}

fun TooltipMakerAPI.addToggle(
    name: String,
    isChecked: Boolean = false,
    buttonHeight: Float = 24f,
    size: ButtonAPI.UICheckboxSize = ButtonAPI.UICheckboxSize.SMALL,
    data: Any? = null,
    pad: Float = 0f,
    onClick: (Boolean) -> Unit = {}
): ButtonAPI {
    val checkbox = this.addCheckbox(
        this.computeStringWidth(name) + buttonHeight + 4f,
        buttonHeight,
        name,
        data,
        size,
        pad
    )
    checkbox.isChecked = isChecked

    checkbox.onClick { onClick(checkbox.isChecked) }

    return checkbox
}

fun TooltipMakerAPI.addNumericTextField(
    width: Float,
    height: Float,
    font: String = Fonts.DEFAULT_SMALL,
    initialValue: Int? = null,
    maxValue: Int = Int.MAX_VALUE,
    allowEmpty: Boolean = true,
    pad: Float = 0f,
    onValueChanged: (String) -> Unit = {}
): ObservedTextField {

    val observedText = ObservedTextField(
        width = width,
        height = height,
        font = font,
        pad = pad,
        initialText = initialValue.toString(),
    )
    observedText.onTextChanged { rawValue ->

        /*val cleanedValue = rawValue.replace("\\D+".toRegex(), "")
        if(cleanedValue.isEmpty()) {
            observedText.textField.text = ""
            observedText.lastText = ""
            return@onTextChanged
        }
        val numericValue = cleanedValue.toIntOrNull() ?: return@onTextChanged
        val sanitizedValue = numericValue.coerceAtMost(maxValue)*/

        val sanitizedValue = rawValue
            .filter { it.isDigit() }
            .toIntOrNull()
            ?.coerceAtMost(maxValue)

        val sanitizedText: String =
            sanitizedValue?.toString()
                ?: if (allowEmpty) {
                    ""
                } else {
                    0.coerceAtMost(maxValue).toString()
                }

        if (sanitizedText != rawValue) {
            observedText.textField.text = sanitizedText
            observedText.lastText = sanitizedText
        }

        onValueChanged(sanitizedText)
    }

    addCustom(observedText.component, 0f)
    return observedText
}

fun Color.setColor(red: Int? = null, green: Int? = null, blue: Int? = null, alpha: Int? = null): Color {
    return Color(red ?: this.red, green ?: this.green, blue ?: this.blue, alpha ?: this.alpha)
}