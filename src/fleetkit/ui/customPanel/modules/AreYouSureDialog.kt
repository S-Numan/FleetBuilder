package fleetkit.ui.customPanel.modules

import fleetkit.core.util.FTTxt
import fleetkit.ui.customPanel.patterns.DialogPanel
import org.lwjgl.input.Keyboard

class AreYouSureDialog(
    title: String? = null,
    prompt: String? = null,
    confirmText: String = FTTxt.txt("confirm"),
    cancelText: String = FTTxt.txt("cancel"),
    confirmButtonShortcut: Int = Keyboard.KEY_G,
    cancelButtonShortcut: Int = Keyboard.KEY_ESCAPE
) : DialogPanel(title) {
    init {
        confirmCancelButtonWidth = 120f
        this.confirmButtonShortcut = confirmButtonShortcut
        this.cancelButtonShortcut = cancelButtonShortcut

        show(500f, 155f, withScroller = true) { ui ->
            if (prompt != null)
                ui.addPara(prompt, 4f)

            addActionButtons(confirmText = confirmText, cancelText = cancelText)
        }
    }
}