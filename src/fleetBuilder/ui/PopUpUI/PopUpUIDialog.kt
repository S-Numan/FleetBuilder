package fleetBuilder.ui.PopUpUI

import com.fs.starfarer.api.ui.CustomPanelAPI
import MagicLib.*
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import java.awt.Color

class PopUpUIDialog(
    override var headerTitle: String? = null,
    val addConfirmButton: Boolean = true,
    val addCancelButton: Boolean = true,
) : BasePopUpUI() {

    private sealed class Entry
    private class ToggleEntry(val label: String, val state: MutableValue) : Entry()
    private class TextEntry(val label: String, val state: MutableValue) : Entry()
    private class ButtonEntry(
        val label: String,
        val dismiss: Boolean,
        val callback: (Map<String, Any>) -> Unit
    ) : Entry()

    private class PaddingEntry(val amount: Float) : Entry()
    private class CustomEntry(val component: UIComponentAPI) : Entry()
    private class ParagraphEntry(
        val text: String,
        val highlights: Array<Color>,
        val highlightWords: Array<out String>
    ) : Entry()

    private data class MutableValue(var value: Any)

    private val entries = mutableListOf<Entry>()
    private val fieldStates = mutableMapOf<String, MutableValue>()

    private var confirmCallback: ((Map<String, Any>) -> Unit)? = null

    fun addToggle(label: String, default: Boolean = false): () -> Boolean {
        val state = MutableValue(default)
        fieldStates[label] = state
        entries.add(ToggleEntry(label, state))
        return { state.value as Boolean }
    }

    fun addTextField(label: String, default: String = ""): () -> String {
        val state = MutableValue(default)
        fieldStates[label] = state
        entries.add(TextEntry(label, state))
        return { state.value as String }
    }

    fun addButton(
        label: String,
        dismissOnClick: Boolean = true,
        onClick: (Map<String, Any>) -> Unit
    ) {
        entries.add(ButtonEntry(label, dismissOnClick, onClick))
    }

    fun addPadding(amount: Float = 10f) {
        entries.add(PaddingEntry(amount))
    }

    fun addCustom(component: UIComponentAPI) {
        entries.add(CustomEntry(component))
    }

    fun addParagraph(text: String, highlights: Array<Color> = emptyArray(), vararg highlightWords: String) {
        entries.add(ParagraphEntry(text, highlights, highlightWords))
    }

    fun onConfirm(callback: (Map<String, Any>) -> Unit) {
        confirmCallback = callback
    }

    override fun createContentForDialog(panelAPI: CustomPanelAPI) {
        val buttonWidth = panelAPI.position.width - (x * 2) - 8f
        val buttonHeight = 24f

        val ui = panelAPI.createUIElement(buttonWidth, panelAPI.position.height, false)

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
                        buttonWidth,
                        buttonHeight,
                        entry.label,
                        null,
                        ButtonAPI.UICheckboxSize.SMALL,
                        0f
                    )
                    checkbox.isChecked = entry.state.value as Boolean
                    checkbox.onClick {
                        entry.state.value = checkbox.isChecked
                    }
                }

                is TextEntry -> {
                    val textField = ui.addTextField(buttonWidth, 0f)
                    textField.isLimitByStringWidth = false
                    textField.text = entry.state.value as String
                }

                is PaddingEntry -> {
                    ui.addSpacer(entry.amount)
                }

                is CustomEntry -> {
                    ui.addComponent(entry.component)
                }

                is ParagraphEntry -> {
                    if (entry.highlights.isNotEmpty() && entry.highlightWords.isNotEmpty()) {
                        ui.addPara(entry.text, 0f, entry.highlights, *entry.highlightWords)
                    } else {
                        ui.addPara(entry.text, 0f)
                    }
                }
            }
        }

        panelAPI.addUIElement(ui).inTL(x, y)

        if (addConfirmButton || addCancelButton) {
            createConfirmAndCancelSection(
                panelAPI,
                addCancelButton = addCancelButton,
                addConfirmButton = addConfirmButton
            )
        }
    }

    override fun advance(amount: Float) {
        super.advance(amount)

    }

    override fun applyConfirmScript() {
        confirmCallback?.invoke(collectFieldStates())
    }

    private fun collectFieldStates(): Map<String, Any> {
        for (entry in entries) {
            if (entry is TextEntry) {
                fieldStates[entry.label]?.value = entry.state.value
            }
        }
        return fieldStates.mapValues { it.value.value }
    }
}