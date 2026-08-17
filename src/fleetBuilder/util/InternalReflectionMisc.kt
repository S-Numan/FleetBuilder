package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.econ.Submarket
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.coreui.refit.ModWidget
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.ReflectionMisc.getCargoPanel
import fleetBuilder.util.ReflectionMisc.getCodexDetailPanel
import fleetBuilder.util.ReflectionMisc.getCoreUI
import fleetBuilder.util.ReflectionMisc.getFleetPanel
import fleetBuilder.util.ReflectionMisc.getFleetTab
import fleetBuilder.util.api.kotlin.isIdle
import fleetBuilder.util.api.kotlin.safeInvoke
import org.magiclib.util.api.getActualCurrentTab

internal object InternalReflectionMisc {
    fun getRefitPanelModWidget(refitPanel: UIPanelAPI): ModWidget? {
        val children = refitPanel.getChildrenCopy()
        var desiredChild: UIPanelAPI? = null
        children.forEach { child ->
            var yup = false

            val panel = child as? UIPanelAPI
            val childsChildren = panel?.getChildrenCopy()
            childsChildren?.forEach { childChildChild ->
                if (childChildChild.getMethodsMatching("removeNotApplicableMods").isNotEmpty()) {
                    yup = true
                    return@forEach
                }
            }
            if (yup) {
                desiredChild = child as? UIPanelAPI
                return@forEach
            }
        }

        return desiredChild?.findChildWithMethod("removeNotApplicableMods") as? ModWidget
    }

    /*fun getSelectedSubmarketInCargoTab(): SubmarketAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.CARGO && campaignUI.isShowingDialog) {
            val dialog = campaignUI.currentInteractionDialog ?: return null
            dialog.interactionTarget?.market ?: return null

            val cargoTab = getCargoTab() ?: return null

            return cargoTab
                .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                .getOrNull(0)
                ?.get(cargoTab) as? SubmarketAPI
        }
        return null
    }*/

    fun getMemberUIHoveredInFleetTabLowerPanel(): UIPanelAPI? {
        val fleetTab = getFleetTab() ?: return null
        val mouseOverMember = fleetTab.safeInvoke("getMousedOverFleetMember") as? FleetMemberAPI ?: return null

        val fleetPanel = getFleetPanel() ?: return null
        val list = fleetPanel.safeInvoke("getList") ?: return null
        val items = list.safeInvoke("getItems") as? List<Any?>
            ?: return null//Core UI box that contains everything related to the fleet member, including the ship, officer, cr, etc. There is one for each member in your fleet.

        // Find UI element of which the mouse is hovering over
        items.forEach { item ->
            if (item == null) return@forEach

            //Get all children for this item
            val children = item.safeInvoke("getChildrenCopy") as? List<Any?> ?: return@forEach

            //Find the UI child with a portrait button
            val foundUI = children.firstOrNull { child ->
                child != null && child.getMethodsMatching(name = "getPortraitButton").isNotEmpty()
            } ?: return@forEach

            //Get FleetMember
            val fields = foundUI.getFieldsMatching(type = FleetMember::class.java)
            if (fields.isEmpty()) return@forEach

            //Return if this item's fleet member is not the one we are hovering over
            val member = fields[0].get(foundUI) as? FleetMemberAPI
            if (member?.id != mouseOverMember.id) return@forEach

            //If we've got here, this is the UI item the mouse is hovering over.
            return foundUI as UIPanelAPI?
        }

        return null
    }

    fun getBelowTitleDeeperPanel(codex: CodexDialog): UIPanelAPI? {
        val belowTitleBarPanel = getCodexDetailPanel(codex)?.findChildWithMethod("addToOverlay") as? UIPanelAPI
        return belowTitleBarPanel?.getChildrenCopy()?.find { (it as? UIPanelAPI)?.getChildrenCopy()?.isNotEmpty() == true } as? UIPanelAPI
    }

    fun getSelectedSubmarket(
    ): SubmarketAPI? {
        val campaignUI = Global.getSector()?.campaignUI ?: return null

        if (campaignUI.isShowingDialog) {
            if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
                val dialog = campaignUI.currentInteractionDialog ?: return null
                dialog.interactionTarget?.market ?: return null

                val fleetTab = getFleetTab() ?: return null

                return fleetTab
                    .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                    .getOrNull(0)
                    ?.get(fleetTab) as? SubmarketAPI
            } else if (campaignUI.getActualCurrentTab() == CoreUITabId.CARGO) {
                val dialog = campaignUI.currentInteractionDialog ?: return null
                dialog.interactionTarget?.market ?: return null

                val cargoPanel = getCargoPanel() ?: return null
                val transferHandler = cargoPanel.safeInvoke("getTransferHandler") ?: return null

                return transferHandler
                    .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                    .getOrNull(0)
                    ?.get(transferHandler) as? SubmarketAPI
            }
        }
        return null
    }

    fun closeCurrentCoreTab() {
        val campUI: CampaignUIAPI? = Global.getSector().campaignUI
        if (campUI != null && !campUI.isIdle()) {
            campUI.safeInvoke("setNextTransitionFast", true)
            val coreUI = getCoreUI()
            coreUI?.safeInvoke("dialogDismissed", coreUI, 0)
        }
    }
}