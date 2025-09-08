package fleetBuilder.ui.popUpUI

import com.fs.starfarer.api.ui.*
import starficz.onClick
import java.awt.Color

open class PopUpUIDialog(
    override var headerTitle: String? = null,
    val addConfirmButton: Boolean = false,
    val addCancelButton: Boolean = false,
    val addCloseButton: Boolean = false,
) : BasePopUpUI() {

    private sealed class Entry
    private class ToggleEntry(
        val label: String,
        val state: MutableValue,
        val clickable: Boolean,
        val onToggle: ((Map<String, Any>) -> Unit)?
    ) : Entry()

    private class TextEntry(
        val label: String,
        val state: MutableValue,
        val onTextChanged: ((Map<String, Any>) -> Unit)?
    ) : Entry()

    private class ButtonEntry(
        val label: String,
        val dismiss: Boolean,
        val callback: (Map<String, Any>) -> Unit
    ) : Entry()

    private class PaddingEntry(val amount: Float) : Entry()
    private class CustomEntry(val component: UIComponentAPI) : Entry()
    private class ParagraphEntry(
        val text: String,
        val alignment: Alignment,
        val font: String,
        val highlights: Array<Color>,
        val highlightWords: Array<out String>
    ) : Entry()

    data class MutableValue(var value: Any)

    private val entries = mutableListOf<Entry>()
    val fieldStates = mutableMapOf<String, MutableValue>()
    val toggleRefs: MutableMap<String, ButtonAPI> = mutableMapOf()
    val textFieldRefs: MutableMap<String, TextFieldAPI> = mutableMapOf()
    private val previousTextValues = mutableMapOf<String, String>()

    private var confirmCallback: ((Map<String, Any>) -> Unit)? = null
    private var cancelCallback: ((Map<String, Any>) -> Unit)? = null
    private var exitCallback: ((Map<String, Any>) -> Unit)? = null

    fun addToggle(
        label: String,
        default: Boolean = false,
        clickable: Boolean = true,
        onToggle: ((Map<String, Any>) -> Unit)? = null
    ): () -> Boolean {
        val state = MutableValue(default)
        fieldStates[label] = state
        entries.add(ToggleEntry(label, state, clickable, onToggle))
        return { state.value as Boolean }
    }

    fun addRadioGroup(
        options: List<String>,
        default: String = options.first(),
        onChanged: (String) -> Unit
    ) {
        for (label in options) {
            addToggle(label, label == default, label != default) { _ ->
                if (toggleRefs[label]?.isChecked == true) {
                    for (other in options) {
                        if (other != label) {
                            toggleRefs[other]?.isChecked = false
                            toggleRefs[other]?.setClickable(true)
                        }
                    }
                    toggleRefs[label]?.setClickable(false)
                    onChanged(label)
                }
            }
        }
    }


    fun addTextField(
        label: String,
        default: String = "",
        onTextChanged: ((Map<String, Any>) -> Unit)? = null
    ): () -> String {
        val state = MutableValue(default)
        fieldStates[label] = state
        previousTextValues[label] = default
        entries.add(TextEntry(label, state, onTextChanged))
        return { state.value as String }
    }

    fun addClampedNumericField(
        label: String,
        maxValue: Int
    ) {
        addTextField(label) { fields ->
            val rawValue = fields[label] as String
            val cleanedValue = rawValue.replace("\\D+".toRegex(), "")

            if (cleanedValue.isEmpty()) {
                textFieldRefs[label]?.text = ""
                return@addTextField
            }

            val numericValue = cleanedValue.toIntOrNull() ?: return@addTextField
            val clampedValue = numericValue.coerceAtMost(maxValue)

            if (clampedValue.toString() != rawValue) {
                textFieldRefs[label]?.text = clampedValue.toString()
            }
        }
    }

    fun addButton(
        label: String,
        dismissOnClick: Boolean = true,
        onClick: (Map<String, Any>) -> Unit
    ) {
        entries.add(ButtonEntry(label, dismissOnClick, onClick))
    }

    fun addPadding(amount: Float = buttonHeight) {
        entries.add(PaddingEntry(amount))
    }

    fun addCustom(component: UIComponentAPI) {
        entries.add(CustomEntry(component))
    }

    fun addParagraph(
        text: String,
        alignment: Alignment = Alignment.TL,
        font: String = Fonts.DEFAULT_SMALL,
        highlights: Array<Color> = emptyArray(),
        highlightWords: Array<String> = emptyArray()
    ) {
        entries.add(ParagraphEntry(text, alignment, font, highlights, highlightWords))
    }

    fun onConfirm(callback: (Map<String, Any>) -> Unit) {
        confirmCallback = callback
    }

    fun onCancel(callback: (Map<String, Any>) -> Unit) {
        cancelCallback = callback
    }

    fun onExit(callback: (Map<String, Any>) -> Unit) {
        exitCallback = callback
    }

    override fun onExit() {
        super.onExit()
        exitCallback?.invoke(collectFieldStates())
    }

    val buttonHeight = 24f

    override fun createContentForDialog() {
        val buttonWidth = panel.position.width - (x * 2)


        val ui = panel.createUIElement(buttonWidth, panel.position.height, false)
        ui.addSpacer(0f).position.inTL(0f, 0f)

        for (entry in entries) {
            when (entry) {
                is ButtonEntry -> {
                    val btn = ui.addButton(entry.label, null, buttonWidth, buttonHeight, 3f)
                    btn.onClick {
                        entry.callback(collectFieldStates())
                        if (entry.dismiss) forceDismiss()
                    }
                }

                is ToggleEntry -> {

                    val checkbox = ui.addCheckbox(
                        ui.computeStringWidth(entry.label) + 28f,
                        buttonHeight,
                        entry.label,
                        null,
                        ButtonAPI.UICheckboxSize.SMALL,
                        0f,
                    )

                    checkbox.isChecked = entry.state.value as Boolean

                    if (!entry.clickable)
                        checkbox.setClickable(false)

                    toggleRefs[entry.label] = checkbox

                    checkbox.onClick {
                        val newValue = checkbox.isChecked
                        entry.state.value = newValue
                        val allStates = collectFieldStates()
                        entry.onToggle?.invoke(allStates)
                    }
                }

                is TextEntry -> {
                    //ui.addPara(entry.label, 0f)
                    val textField = ui.addTextField(buttonWidth, 0f)
                    textField.isLimitByStringWidth = false
                    textField.maxChars = 255
                    textField.text = entry.state.value as String
                    textFieldRefs[entry.label] = textField
                }

                is PaddingEntry -> ui.addSpacer(entry.amount)
                is CustomEntry -> {
                    ui.addCustom(entry.component, 0f)
                }

                is ParagraphEntry -> {
                    ui.setParaFont(entry.font)

                    if (entry.highlights.isNotEmpty() && entry.highlightWords.isNotEmpty()) {
                        ui.addPara(entry.text, 0f, entry.highlights, *entry.highlightWords).setAlignment(entry.alignment)
                    } else {
                        ui.addPara(entry.text, 0f).setAlignment(entry.alignment)
                    }
                }
            }
        }

        panel.addUIElement(ui).inTL(x, y)

        if (addConfirmButton || addCancelButton) {
            createConfirmAndCancelSection(
                addCancelButton = addCancelButton,
                addConfirmButton = addConfirmButton
            )
        }
        if (addCloseButton)
            addCloseButton()
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        for (entry in entries) {
            if (entry is TextEntry) {
                val label = entry.label
                val field = textFieldRefs[label] ?: continue
                val currentText = field.text
                val previousText = previousTextValues[label]
                if (currentText != previousText) {
                    fieldStates[label]?.value = currentText
                    previousTextValues[label] = currentText
                    val allStates = collectFieldStates()
                    entry.onTextChanged?.invoke(allStates)
                }
            }
        }
    }

    override fun applyConfirmScript() {
        confirmCallback?.invoke(collectFieldStates())
    }

    override fun applyCancelScript() {
        cancelCallback?.invoke(collectFieldStates())
    }

    private fun collectFieldStates(): Map<String, Any> {
        // Ensure all text fields are up-to-date before returning
        for ((label, field) in textFieldRefs) {
            fieldStates[label]?.value = field.text
        }
        for ((label, field) in toggleRefs) {
            fieldStates[label]?.value = field.isChecked
        }

        return fieldStates.mapValues { it.value.value }
    }
}