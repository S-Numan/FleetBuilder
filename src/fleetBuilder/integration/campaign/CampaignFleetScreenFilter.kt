package fleetBuilder.integration.campaign

import MagicLib.*
import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TextFieldAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.fleet.FleetData
import fleetBuilder.ui.FleetFilterPanel
import fleetBuilder.util.MISC
import fleetBuilder.util.findChildWithMethod
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.getChildrenCopy
import fleetBuilder.util.getCompatibleDLessHullId
import fleetBuilder.util.getShipNameWithoutPrefix
import org.lwjgl.input.Mouse
import starficz.ReflectionUtils.invoke

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

        val fleetSidePanel = MISC.getFleetSidePanel() ?: return
        val fleetPanel = MISC.getFleetPanel() ?: return

        //On fleet panel change
        if (prevFleetPanel !== fleetPanel) {

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