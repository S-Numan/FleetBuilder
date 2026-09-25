package fleetBuilder.features.filters.injection

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.features.filters.filterPanels.FleetFilterPanel
import fleetBuilder.features.filters.filterPanels.FleetFilterPanel.Companion.removePreviousIfAny
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.reflection.InternalReflectionMisc
import org.magiclib.util.api.getActualCurrentTab
import org.magiclib.util.reflection.boxed.BoxedFleetTab

internal class CampaignFleetScreenFilter : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var prevFleetPanel: UIPanelAPI? = null
    var filterPanel: FleetFilterPanel? = null

    override fun advance(amount: Float) {
        if (!Global.getSector()!!.isPaused) return

        val campaignState = Global.getSector()!!.campaignUI
        if (campaignState.getActualCurrentTab() != CoreUITabId.FLEET) {
            if (prevFleetPanel != null)
                prevFleetPanel = null
            if (filterPanel != null) {
                removePreviousIfAny()
                filterPanel = null
            }
            return
        }

        val fleetTab = BoxedFleetTab.get() ?: return
        val fleetPanel = fleetTab.fleetPanel

        //On fleet panel appearing
        if (filterPanel == null) {
            val fleetSidePanel = InternalReflectionMisc.getFleetSidePanel(fleetTab) ?: return

            filterPanel = FleetFilterPanel(20f, fleetTab, fleetSidePanel)
        }

        //On fleet panel change
        if (prevFleetPanel !== fleetPanel) {
            if (filterPanel == null) return
            filterPanel!!.resetText()

            val fleetSidePanel = InternalReflectionMisc.getFleetSidePanel(fleetTab) ?: return
            //If the FilterPanel is not in fleetSidePanel
            val currentFilterPanel = fleetSidePanel.getChildrenCopy().find { (it as? CustomPanelAPI)?.plugin as? FleetFilterPanel != null }
            if (currentFilterPanel == null) {
                //Remake and add it!
                filterPanel = FleetFilterPanel(20f, fleetTab, fleetSidePanel)
            } else {

            }

            prevFleetPanel = fleetPanel
        }
    }
}