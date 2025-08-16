package fleetBuilder.integration.campaign

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.SubmarketPlugin
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.util.DialogUtil
import fleetBuilder.util.Dialogs
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.invoke
import starficz.findChildWithMethod
import starficz.getChildrenCopy

class CargoAutoManagerOpener : CampaignInputListener {
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
        if (sector.currentlyOpenMarket == null || ReflectionMisc.isCodexOpen() || DialogUtil.isPopUpUIOpen()) return

        val cargoTab = ReflectionMisc.getCargoTab() ?: return

        val submarketButtonParent = cargoTab.findChildWithMethod("showSubmarketTextDialog") as? UIPanelAPI ?: return
        val submarketButtons = submarketButtonParent.getChildrenCopy()

        submarketButtons.forEach { submarketButton ->
            val fader = submarketButton.invoke("getMouseoverHighlightFader") as? Fader ?: return@forEach

            if (fader.isFadingIn || fader.brightness == 1f) {

                val tool = submarketButton.invoke("getTooltip") as? TooltipMakerAPI ?: return@forEach
                val pluginField = tool.getFieldsMatching(fieldAccepts = SubmarketPlugin::class.java, searchSuperclass = true).getOrNull(0)
                val submarketPlugin = pluginField?.get(tool) as? SubmarketPlugin
                    ?: return@forEach
                val selectedSubmarket = submarketPlugin.submarket

                val coreUI = ReflectionMisc.getCoreUI(topDialog = false) as CoreUIAPI
                if (!submarketPlugin.getOnClickAction(coreUI).equals(SubmarketPlugin.OnClickAction.OPEN_SUBMARKET)) return@forEach
                if (!submarketPlugin.isEnabled(coreUI)) return@forEach
                if (!submarketPlugin.isFreeTransfer) return@forEach//Temporary to avoid cheating when WIP
                if (submarketPlugin is LocalResourcesSubmarketPlugin) return@forEach

                Dialogs.openSubmarketCargoAutoManagerDialog(selectedSubmarket)

                event.consume()
            }
        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI?>?) {}

    override fun processCampaignInputPostCore(events: List<InputEventAPI?>?) {}

}