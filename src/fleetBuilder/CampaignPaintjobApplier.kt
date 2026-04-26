package fleetBuilder

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignEngine
import com.fs.starfarer.campaign.CampaignEntity
import com.fs.starfarer.ui.impl.StandardTooltipV2
import com.fs.starfarer.ui.newui.FleetMemberRecoveryDialog
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.api.kotlin.findChildWithMethod
import fleetBuilder.util.api.kotlin.isIdle
import fleetBuilder.util.api.kotlin.safeInvoke
import org.magiclib.paintjobs.MagicPaintjobManager

internal class CampaignPaintjobApplier : EveryFrameScript {
    var errorOccured = false

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true
    override fun advance(amount: Float) {
        if (errorOccured) return

        try {
            applyToCommandTooltip()
            applyToInteractionDialog()
            applyToCampaignCircleFleets()
        } catch (e: Exception) {
            Global.getLogger(this::class.java).error("Error when trying to apply paintjobs in the campaign", e)
            errorOccured = true
        }
    }

    private var prevLocation: LocationAPI? = null
    private var fleetsApplied = mutableSetOf<String>()
    private fun applyToCampaignCircleFleets() {
        val curLocation = Global.getSector().currentLocation
        if (curLocation !== prevLocation) { // If location change
            fleetsApplied.clear()

            prevLocation = curLocation
        }

        curLocation.fleets.forEach { fleet ->
            if (!fleet.isVisibleToPlayerFleet)
                return@forEach
            if (fleet.id in fleetsApplied)
                return@forEach

            val views = fleet.views ?: return@forEach
            if (views.isEmpty()) return@forEach

            views.filterNotNull().forEach { view ->
                val member = view.member ?: return@forEach

                PaintjobApplierUtils.changeIconSprite(member, view)
            }
            fleetsApplied.add(fleet.id)
        }
    }

    private var centerTooltipHash: Int = 0
    private fun applyToInteractionDialogSelectCraft() {
        val sector = Global.getSector()
        val ui = sector.campaignUI
        val visual = ui.currentInteractionDialog.visualPanel as? UIPanelAPI

        val centerTooltip = visual?.getChildrenCopy()?.findChildWithMethod(name = "fleetMemberClicked") as? UIPanelAPI

        val centerTooltipHash = centerTooltip.hashCode()
        // Only try to replace sprites once every time the shown tooltip changes
        if (this.centerTooltipHash == centerTooltipHash)
            return

        this.centerTooltipHash = centerTooltipHash

        val innerPanel = centerTooltip?.safeInvoke("getInnerPanel") as? UIPanelAPI ?: return
        val fleetList = innerPanel.getChildrenCopy().findChildWithMethod(name = "turnOffCRandHullBars") ?: return

        PaintjobApplierUtils.applyPaintjobsToShipList(fleetList, if (centerTooltip is FleetMemberRecoveryDialog) 1 else 0)
    }

    private var lastOptionsHash: Int? = null
    private fun applyToInteractionDialog() {
        val sector = Global.getSector()
        val ui = sector.campaignUI
        if (ui.currentInteractionDialog == null) return

        val battle = (ui.currentInteractionDialog?.plugin?.context as? FleetEncounterContext)?.battle
        val interactionFleet = if (battle != null) {
            battle.nonPlayerCombined
        } else {
            ui.currentInteractionDialog.interactionTarget as? CampaignFleetAPI
        }

        // If no paintjobs in either fleet, do not continue
        if (interactionFleet != null && interactionFleet.fleetData.membersListCopy.none { member -> MagicPaintjobManager.hasPaintjob(member) }
            && sector.playerFleet.fleetData.membersListCopy.none { member -> MagicPaintjobManager.hasPaintjob(member) })
            return

        applyToInteractionDialogSelectCraft()

        val optionsHash = ui.currentInteractionDialog.optionPanel.savedOptionList
            .map { it.hashCode() }
            .hashCode()

        // Only try to replace sprites once every time the options list changes.
        if (lastOptionsHash == optionsHash)
            return

        lastOptionsHash = optionsHash


        val visual = ui.currentInteractionDialog.visualPanel as? UIPanelAPI

        val topRightPanel = visual?.getChildrenCopy()?.findLast { it.getFieldsMatching(type = FleetEncounterContextPlugin::class.java).isNotEmpty() } as? UIPanelAPI ?: return
        val topRightFleetPanel = (topRightPanel.getChildrenCopy().getOrNull(0) as? UIPanelAPI)
            ?.getChildrenCopy()?.getOrNull(0) as? UIPanelAPI ?: return// Scroller
        val topRightPlayerFleet = (topRightFleetPanel.getChildrenCopy().getOrNull(0) as? UIPanelAPI)?.safeInvoke("getAllLists") as? List<*> ?: return
        val topRightEnemyFleet = (topRightFleetPanel.getChildrenCopy().getOrNull(1) as? UIPanelAPI)?.safeInvoke("getAllLists") as? List<*> ?: return

        topRightPlayerFleet.getOrNull(0)?.let {
            if (it !is UIComponentAPI) return@let
            PaintjobApplierUtils.applyPaintjobsToShipList(it)
        }
        topRightEnemyFleet.getOrNull(0)?.let {
            if (it !is UIComponentAPI) return@let
            PaintjobApplierUtils.applyPaintjobsToShipList(it, 1)
        }
    }

    private var currentHoveredFleetID: String? = null
    private fun applyToCommandTooltip() {
        if (!Global.getSector().campaignUI.isIdle())
            return
        val engine = CampaignEngine.getInstance()
        val tooltip = engine.tooltipManager
        val hoveredFleet = tooltip?.getFieldsMatching(type = CampaignEntity::class.java)?.getOrNull(0)?.get(tooltip) as? CampaignFleetAPI
        if (hoveredFleet == null) {
            currentHoveredFleetID = null
            return
        }

        // Only replace the sprites in the tooltip once
        if (hoveredFleet.id == currentHoveredFleetID)
            return

        currentHoveredFleetID = hoveredFleet.id

        val paintJobMembers = hoveredFleet.fleetData.membersListCopy.filter { member -> MagicPaintjobManager.hasPaintjob(member) }
        if (paintJobMembers.isEmpty())
            return

        val tooltipPanel = tooltip.getFieldsMatching(type = StandardTooltipV2::class.java).getOrNull(0)?.get(tooltip) as? UIPanelAPI
            ?: return
        val tooltipPanelChildChildren = (tooltipPanel.getChildrenCopy().getOrNull(0) as? UIPanelAPI)?.getChildrenCopy()
            ?: return

        val shipList = tooltipPanelChildChildren.findChildWithMethod(name = "turnOffCRandHullBars") ?: return

        PaintjobApplierUtils.applyPaintjobsToShipList(shipList, 1)
    }
}