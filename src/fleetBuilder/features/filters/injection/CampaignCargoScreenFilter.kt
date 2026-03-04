package fleetBuilder.features.filters.injection

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.features.filters.filterPanels.BaseFilterPanel
import fleetBuilder.features.filters.filterPanels.CargoFilterPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.safeInvoke
import starficz.getChildrenCopy

class CampaignCargoScreenFilter : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }


    var prevCargoPanel: UIPanelAPI? = null

    var playerFilterPanel: CargoFilterPanel? = null
    var entityFilterPanel: CargoFilterPanel? = null

    override fun advance(amount: Float) {
        if (!Global.getSector().isPaused) return

        val campaignState = Global.getSector().campaignUI
        if (campaignState.getActualCurrentTab() != CoreUITabId.CARGO) {
            if (playerFilterPanel != null) {
                prevCargoPanel?.removeComponent(playerFilterPanel!!.getPanel())
                playerFilterPanel = null
            }
            if (entityFilterPanel != null) {
                prevCargoPanel?.removeComponent(entityFilterPanel!!.getPanel())
                entityFilterPanel = null
            }
            if (prevCargoPanel != null)
                prevCargoPanel = null

            return
        }

        val cargoPanel = ReflectionMisc.getCargoPanel() ?: return

        fun makePlayerFilter() {
            val playerCargoDisplay = cargoPanel.safeInvoke("getPlayerCargoDisplay") as? UIPanelAPI
            if (playerCargoDisplay != null) {
                playerFilterPanel = CargoFilterPanel(200f, 19f, playerCargoDisplay)
            }
        }

        val entityCargoDisplay = cargoPanel.safeInvoke("getEntityCargoDisplay") as? UIPanelAPI
        fun makeEntityFilter() {
            if (entityCargoDisplay?.safeInvoke("getMode")?.toString() == "CARGO_ENTITY") {
                entityFilterPanel = CargoFilterPanel(200f, 19f, entityCargoDisplay)
            }
        }

        if (playerFilterPanel == null)
            makePlayerFilter()

        if (entityFilterPanel == null || entityCargoDisplay?.getChildrenCopy()?.none { (it as? CustomPanelAPI)?.plugin is BaseFilterPanel } == true) {
            makeEntityFilter()
        }

        // On cargo panel change
        if (prevCargoPanel !== cargoPanel) {
            prevCargoPanel = cargoPanel
            makePlayerFilter()
            makeEntityFilter()
        }
    }
}