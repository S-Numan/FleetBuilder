package fleetBuilder.integration.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.FleetFilterPanel
import fleetBuilder.util.MISC
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.getChildrenCopy

class CampaignFleetScreenFilter : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var prevFleetPanel: UIPanelAPI? = null

    override fun advance(amount: Float) {
        if (!Global.getSector().isPaused) return

        val campaignState = Global.getSector().campaignUI
        if (campaignState.getActualCurrentTab() != CoreUITabId.FLEET) {
            if (prevFleetPanel != null)
                prevFleetPanel = null
            return
        }

        val fleetPanel = MISC.getFleetPanel() ?: return

        //On fleet panel change
        if (prevFleetPanel !== fleetPanel) {

            val fleetSidePanel = MISC.getFleetSidePanel() ?: return

            //If FilterPanel exists, remove it
            val currentFilterPanel = fleetSidePanel.getChildrenCopy().find { (it as? CustomPanelAPI)?.plugin as? FleetFilterPanel != null }
            if (currentFilterPanel != null) {
                fleetSidePanel.removeComponent(currentFilterPanel)
            }
            //Make new filter panel
            FleetFilterPanel(20f, fleetPanel, fleetSidePanel)

            prevFleetPanel = fleetPanel
        }


    }
}