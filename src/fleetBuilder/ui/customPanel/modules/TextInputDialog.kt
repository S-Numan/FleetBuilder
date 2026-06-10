package fleetBuilder.ui.customPanel.modules

import fleetBuilder.otherMods.starficz.width
import fleetBuilder.ui.addExcludeTextField
import fleetBuilder.ui.customPanel.patterns.DialogPanel
import org.lwjgl.input.Keyboard

class TextInputDialog(
    title: String? = null,
    prompt: String? = null,
    excludedCharacters: String = "\\/:*?\"<>|",
    onConfirmAction: (String) -> Unit
) : DialogPanel(title) {
    init {
        confirmCancelButtonWidth = 120f
        confirmButtonShortcut = Keyboard.KEY_NONE
        cancelButtonShortcut = Keyboard.KEY_NONE
        //doesConfirmDismiss = false

        show(500f, 155f) { ui ->
            if (prompt != null)
                ui.addPara(prompt, 4f)
            val textField = ui.addExcludeTextField(ui.width, 30f, pad = 10f, excludedCharacters = excludedCharacters).textField
            textField.grabFocus()

            addActionButtons(confirmText = "OK")

            onConfirm {
                textField.text?.let {
                    onConfirmAction(it)
                }
            }

            advance {
                if (Keyboard.isKeyDown(Keyboard.KEY_RETURN) || Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER)) {
                    applyConfirmScript()
                    dismiss()
                }
            }
        }
    }
}