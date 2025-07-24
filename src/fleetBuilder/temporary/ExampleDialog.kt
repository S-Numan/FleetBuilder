package fleetBuilder.temporary

import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.ui.PopUpUI.BasePopUpUI

class ExampleDialog(
    override var headerTitle: String? = null
) : BasePopUpUI() {
    override fun createContentForDialog(panelAPI: CustomPanelAPI) {
        val tooltip = panelToInfluence!!.createUIElement(panelToInfluence!!.position.width - 30, panelToInfluence!!.position.height - y, true)
        panelToInfluence!!.addUIElement(tooltip).inTL(x, y)

        tooltip.setParaInsigniaLarge()
        tooltip.addPara("Test message 1", 5f)
        tooltip.addPara("Test message 2", 5f)

        createConfirmAndCancelSection(panelAPI, addCancelButton = false)
    }

    override fun applyConfirmScript() {

    }
}