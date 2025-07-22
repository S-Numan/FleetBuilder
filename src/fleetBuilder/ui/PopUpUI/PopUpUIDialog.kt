package fleetBuilder.ui.PopUpUI

import com.fs.starfarer.api.ui.CustomPanelAPI
import MagicLib.*
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import java.awt.Color

class PopUpUIDialog(
    override var headerTitle: String? = null,
    val addConfirmButton: Boolean = true,
    val addCancelButton: Boolean = true,
) : BasePopUpUI() {

    private sealed class Entry
    private class ToggleEntry(val label: String, val state: MutableState) : Entry()
    private class ButtonEntry(val label: String, val dismiss: Boolean, val callback: (Map<String, Boolean>) -> Unit) :
        Entry()

    private class PaddingEntry(val amount: Float) : Entry()
    private class CustomEntry(val component: UIComponentAPI) : Entry()
    private class ParagraphEntry(
        val text: String,
        val highlights: Array<Color>,
        val highlightWords: Array<out String>
    ) : Entry()

    private data class MutableState(var value: Boolean)

    private val entries = mutableListOf<Entry>()
    private val toggleStates = mutableMapOf<String, MutableState>()

    private var confirmCallback: ((Map<String, Boolean>) -> Unit)? = null

    fun addToggle(label: String, default: Boolean = false): () -> Boolean {
        val state = MutableState(default)
        toggleStates[label] = state
        entries.add(ToggleEntry(label, state))
        return { state.value }
    }

    fun addButton(
        label: String,
        dismissOnClick: Boolean = true,
        onClick: (Map<String, Boolean>) -> Unit
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


    fun onConfirm(callback: (Map<String, Boolean>) -> Unit) {
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
                        entry.callback(getToggleStates())
                        if (entry.dismiss) forceDismiss()
                    }
                }

                /*is ToggleEntry -> {
                    val btn = ui.addButton(
                        "${entry.label}: ${if (entry.state.value) "ON" else "OFF"}",
                        null,
                        buttonWidth,
                        buttonHeight,
                        3f
                    )
                    btn.onClick {
                        entry.state.value = !entry.state.value
                        btn.text = "${entry.label}: ${if (entry.state.value) "ON" else "OFF"}"
                    }
                }*/
                is ToggleEntry -> {
                    val checkbox = ui.addCheckbox(
                        buttonWidth,
                        buttonHeight,
                        entry.label,
                        null,
                        ButtonAPI.UICheckboxSize.SMALL,
                        0f
                    )
                    checkbox.isChecked = entry.state.value
                    checkbox.onClick {
                        entry.state.value = checkbox.isChecked
                    }
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

    override fun applyConfirmScript() {
        confirmCallback?.invoke(getToggleStates())
    }

    private fun getToggleStates(): Map<String, Boolean> {
        return toggleStates.mapValues { it.value.value }
    }
}