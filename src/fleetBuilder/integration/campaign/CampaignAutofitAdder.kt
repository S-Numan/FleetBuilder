package fleetBuilder.integration.campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import fleetBuilder.config.ModSettings
import fleetBuilder.ui.autofit.AutofitPanelCreator
import fleetBuilder.util.MISC
import fleetBuilder.util.getActualCurrentTab

internal class CampaignAutofitAdder : CampaignInputListener {
    override fun getListenerInputPriority(): Int {
        return 1
    }

    override fun processCampaignInputPreCore(events: MutableList<InputEventAPI>) {
        if (!ModSettings.autofitMenuEnabled) return

        val sector = Global.getSector() ?: return

        //if (!sector.isPaused) return //Return if not paused
        val ui = sector.campaignUI ?: return

        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN && event.eventValue == ModSettings.autofitMenuHotkey && ui.getActualCurrentTab() == CoreUITabId.REFIT) {

                //Open autofit menu in refit screen

                val refitTab = MISC.getRefitTab() ?: continue

                AutofitPanelCreator.toggleAutofitButton(refitTab, true)

                event.consume();
            }
        }
    }

    override fun processCampaignInputPreFleetControl(events: MutableList<InputEventAPI>) {

    }

    override fun processCampaignInputPostCore(events: MutableList<InputEventAPI>) {

    }
}