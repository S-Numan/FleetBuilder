package fleetBuilder.temporary

import fleetBuilder.ui.PopUpUI.BasePopUpUI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.util.DisplayMessage
import MagicLib.*

class PasteFleetDialog(
    override var headerTitle: String? = null
) : BasePopUpUI() {

    private lateinit var button1: ButtonAPI
    private lateinit var button2: ButtonAPI
    private lateinit var button3: ButtonAPI

    override fun createContentForDialog(panelAPI: CustomPanelAPI) {
        val buttonWidth = 200f
        val buttonHeight = 24f
        val spacing = 10f

        val totalHeight = buttonHeight * 3 + spacing * 2
        val ui = panelAPI.createUIElement(buttonWidth, totalHeight, false)

        button1 = ui.addButton("Add to fleet", null, buttonWidth, buttonHeight, 3f)
        button1.onClick {
            DisplayMessage.showMessage("1")
            forceDismiss()
        }

        ui.addSpacer(spacing)

        button2 = ui.addButton("Replace fleet", null, buttonWidth, buttonHeight, 3f)
        button2.onClick {
            DisplayMessage.showMessage("2")
            forceDismiss()
        }

        ui.addSpacer(spacing)

        button3 = ui.addButton("Replace fleet with commander", null, buttonWidth, buttonHeight, 3f)
        button3.onClick {
            DisplayMessage.showMessage("3")
            forceDismiss()
        }

        panelAPI.addUIElement(ui).inTL(x, y)
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        super.processInput(events)
    }
}