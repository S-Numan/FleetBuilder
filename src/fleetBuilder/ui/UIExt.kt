package fleetBuilder.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.TooltipMakerAPI
import fleetBuilder.otherMods.starficz.Font
import fleetBuilder.otherMods.starficz.getFontPath
import fleetBuilder.ui.common.ObservedTextField
import fleetBuilder.util.api.kotlin.safeInvoke
import org.magiclib.kotlin.setAlpha
import java.awt.Color

/**
 * Add shortcut to button, but do not show the shortcut in the UI
 */
fun ButtonAPI.addShortcutNoShow(key: Int) {
    this.safeInvoke("addExtraShortcut", key, false, false, false)
}

/**
 * Default parameters for addCheckbox
 */
fun TooltipMakerAPI.addCheckboxD(
    name: String,
    isChecked: Boolean = false,
    height: Float = 24f,
    size: ButtonAPI.UICheckboxSize = ButtonAPI.UICheckboxSize.SMALL,
    data: Any? = null,
    pad: Float = 0f,
    font: Font = Font.INSIGNIA_15,
    textColor: Color = Global.getSettings().brightPlayerColor,
    //onClick: (Boolean) -> Unit = {}
): ButtonAPI {
    val fontPath = getFontPath(font)
    val checkbox = this.addCheckbox(
        Global.getSettings().computeStringWidth(name, fontPath) + height + 4f,
        height,
        name,
        data,
        fontPath,
        textColor,
        size,
        pad
    )
    checkbox.isChecked = isChecked

    //checkbox.onClick { onClick(checkbox.isChecked) }

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

/**
 * Sets the color properties of a Color object. Any unset/null parameters will be unchanged
 *
 * @param red The new red value for the color (optional).
 * @param green The new green value for the color (optional).
 * @param blue The new blue value for the color (optional).
 * @param alpha The new alpha value for the color (optional).
 * @return A new Color object with the specified color properties.
 */
fun Color.setColor(red: Int? = null, green: Int? = null, blue: Int? = null, alpha: Int? = null): Color {
    return Color(red ?: this.red, green ?: this.green, blue ?: this.blue, alpha ?: this.alpha)
}

/**
 * Creates a new Color object with the same RGB values as the original color, but with the alpha value scaled by the provided multiplier.
 *
 * @param alphaMult The multiplier to apply to the original alpha value. Must be between 0 and 1 (inclusive).
 * @return A new Color object with the same RGB values as the original color, but with the alpha value scaled by the provided multiplier.
 */
fun Color.withAlphaMult(alphaMult: Float): Color {
    val clampedAlphaMult = alphaMult.coerceIn(0f, 1f)
    return this.setAlpha((clampedAlphaMult * 255).toInt())
}