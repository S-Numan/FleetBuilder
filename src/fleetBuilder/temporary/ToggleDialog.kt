package fleetBuilder.temporary

import fleetBuilder.ui.PopUpUI.BasePopUpUI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.util.DisplayMessage
import MagicLib.*

class ToggleDialog(
    override var headerTitle: String? = null
) : BasePopUpUI() {

    private var toggleOneState = false
    private var toggleTwoState = false

    private lateinit var toggleOneButton: ButtonAPI
    private lateinit var toggleTwoButton: ButtonAPI

    override fun createContentForDialog(panelAPI: CustomPanelAPI) {
        val buttonWidth = 200f
        val buttonHeight = 24f
        val padding = 10f

        // Create panel for buttons
        val buttonPanel = panelAPI.createCustomPanel(420f, buttonHeight * 2 + padding, null)

        // Toggle One
        val ui1 = buttonPanel.createUIElement(buttonWidth, buttonHeight, false)
        toggleOneButton = ui1.addButton("Toggle Option 1: OFF", null, buttonWidth, buttonHeight, 3f)
        toggleOneButton.onClick {
            toggleOneState = !toggleOneState
            toggleOneButton.text = "Toggle Option 1: " + if (toggleOneState) "ON" else "OFF"
        }
        buttonPanel.addUIElement(ui1).inTL(0f, 5f)

        // Toggle Two
        val ui2 = buttonPanel.createUIElement(buttonWidth, buttonHeight, false)
        toggleTwoButton = ui2.addButton("Toggle Option 2: OFF", null, buttonWidth, buttonHeight, 3f)
        toggleTwoButton.onClick {
            toggleTwoState = !toggleTwoState
            toggleTwoButton.text = "Toggle Option 2: " + if (toggleTwoState) "ON" else "OFF"
        }
        buttonPanel.addUIElement(ui2).belowLeft(ui1, 5f)

        panelAPI.addComponent(buttonPanel).inTL(x, y)

        createConfirmAndCancelSection(panelAPI, addCancelButton = false)
    }

    override fun applyConfirmScript() {
        if (toggleOneState) {
            DisplayMessage.showMessage("Toggle 1 was ON")
        }
        if (toggleTwoState) {
            DisplayMessage.showMessage("Toggle 2 was ON")
        }
    }
}