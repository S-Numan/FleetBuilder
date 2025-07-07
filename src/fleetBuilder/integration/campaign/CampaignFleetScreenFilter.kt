package fleetBuilder.integration.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.FleetFilterPanel
import fleetBuilder.ui.FleetFilterPanel.Companion.removePreviousIfAny
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
    var filterPanel: FleetFilterPanel? = null

    override fun advance(amount: Float) {
        if (!Global.getSector().isPaused) return

        val campaignState = Global.getSector().campaignUI
        if (campaignState.getActualCurrentTab() != CoreUITabId.FLEET) {
            if (prevFleetPanel != null)
                prevFleetPanel = null
            if (filterPanel != null) {
                removePreviousIfAny()
                filterPanel = null
            }
            return
        }

        val fleetPanel = MISC.getFleetPanel() ?: return

        //On fleet panel appearing
        if (filterPanel == null) {
            val fleetSidePanel = MISC.getFleetSidePanel() ?: return

            filterPanel = FleetFilterPanel(20f, fleetSidePanel)
        }

        //On fleet panel change
        if (prevFleetPanel !== fleetPanel) {
            if (filterPanel == null) return
            filterPanel!!.resetText()

            val fleetSidePanel = MISC.getFleetSidePanel() ?: return
            //If the FilterPanel is not in fleetSidePanel
            val currentFilterPanel = fleetSidePanel.getChildrenCopy().find { (it as? CustomPanelAPI)?.plugin as? FleetFilterPanel != null }
            if (currentFilterPanel == null) {
                //Remake and add it!
                filterPanel = FleetFilterPanel(20f, fleetSidePanel)
            } else {

            }

            prevFleetPanel = fleetPanel
        }


    }
}