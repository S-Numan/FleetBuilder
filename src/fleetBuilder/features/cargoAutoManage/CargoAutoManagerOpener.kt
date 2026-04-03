package fleetBuilder.features.cargoAutoManage

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.SubmarketPlugin
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.kotlin.getActualCurrentTab
import fleetBuilder.util.kotlin.safeInvoke
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.getChildrenCopy

internal class CargoAutoManagerOpener : CampaignInputListener {
    override fun getListenerInputPriority(): Int = 1

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        val ui = sector.campaignUI ?: return

        events.forEach { event ->
            if (event.isConsumed) return@forEach

            if (event.eventType == InputEventType.MOUSE_DOWN && event.isRMBDownEvent && ui.getActualCurrentTab() == CoreUITabId.CARGO) {
                handleCargoMouseEvents(event, sector)
            }
        }
    }

    private fun handleCargoMouseEvents(event: InputEventAPI, sector: SectorAPI) {
        if (sector.currentlyOpenMarket == null || ReflectionMisc.isCodexOpen() || DialogUtils.Companion.isPopUpPanelOpen()) return

        val cargoPanel = ReflectionMisc.getCargoPanel() ?: return

        val submarketButtonParent = cargoPanel.findChildWithMethod("showSubmarketTextDialog") as? UIPanelAPI ?: return
        val submarketButtons = submarketButtonParent.getChildrenCopy()

        submarketButtons.forEach { submarketButton ->
            val fader = submarketButton.safeInvoke("getMouseoverHighlightFader") as? Fader ?: return@forEach

            if (fader.isFadingIn || fader.brightness == 1f) {

                val tool = submarketButton.safeInvoke("getTooltip") as? TooltipMakerAPI ?: return@forEach
                val pluginField = tool.getFieldsMatching(fieldAccepts = SubmarketPlugin::class.java, searchSuperclass = true).getOrNull(0)
                val submarketPlugin = pluginField?.get(tool) as? SubmarketPlugin
                    ?: return@forEach
                val selectedSubmarket = submarketPlugin.submarket

                val coreUI = ReflectionMisc.getCoreUI() as CoreUIAPI
                if (!submarketPlugin.getOnClickAction(coreUI).equals(SubmarketPlugin.OnClickAction.OPEN_SUBMARKET)) return@forEach
                if (!submarketPlugin.isEnabled(coreUI)) return@forEach
                if (!submarketPlugin.isFreeTransfer) return@forEach//Temporary to avoid cheating when WIP
                if (submarketPlugin is LocalResourcesSubmarketPlugin) return@forEach

                openSubmarketCargoAutoManagerDialog(selectedSubmarket)

                event.consume()
            }
        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI?>?) {}

    override fun processCampaignInputPostCore(events: List<InputEventAPI?>?) {}

}

fun openSubmarketCargoAutoManagerDialog(
    selectedSubmarket: SubmarketAPI,
    instantUp: Boolean = false
) {
    CargoAutoManageUIPlugin(selectedSubmarket, 1000f, 1000f, instantUp).getPanel()
}