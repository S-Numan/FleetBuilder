package fleetBuilder.integration.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import fleetBuilder.util.MISC
import fleetBuilder.util.MISC.getMemberUIHoveredInFleetTabLowerPanel
import fleetBuilder.util.MISC.getSelectedSubmarketInFleetTab
import fleetBuilder.util.MISC.getViewedFleetInFleetPanel
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.getChildrenCopy
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.invoke

class StoreOfficersInCargo : CampaignInputListener {
    override fun getListenerInputPriority(): Int = 10

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) return
        val ui = sector.campaignUI ?: return
        if (ui.currentInteractionDialog == null || ui.currentInteractionDialog.interactionTarget == null || ui.currentInteractionDialog.interactionTarget.market == null) return
        if (ui.getActualCurrentTab() != CoreUITabId.FLEET) return

        val submarket = getSelectedSubmarketInFleetTab() ?: return
        if (submarket.faction != sector.playerFaction || !submarket.plugin.isFreeTransfer) return //Don't sell officers to other factions
        val viewedFleet = getViewedFleetInFleetPanel()
        if (viewedFleet !== sector.playerFleet.fleetData)//If we aren't looking at the user's fleet, don't continue
            return


        fun storeOfficer(memberUI: UIPanelAPI) {
            val mouseOverMember = memberUI.getFieldsMatching(type = FleetMember::class.java).getOrNull(0)?.get(memberUI) as? FleetMemberAPI
                ?: return
            val captain = mouseOverMember.captain
            if (!captain.isDefault && !captain.isPlayer && !captain.memoryWithoutUpdate.contains(Misc.CAPTAIN_UNREMOVABLE)) {
                captain.memoryWithoutUpdate.set("\$FB_stored_officer", true)
                captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                sector.playerFleet.fleetData.removeOfficer(captain)
            }
        }

        events.forEach { event ->
            if (event.isConsumed) return@forEach

            if (event.eventType == InputEventType.KEY_DOWN && event.eventValue == Keyboard.KEY_S) {
                val memberUI = getMemberUIHoveredInFleetTabLowerPanel() ?: return@forEach
                storeOfficer(memberUI)
            } else if (event.isLMBDownEvent) {
                val memberUI = getMemberUIHoveredInFleetTabLowerPanel() ?: return@forEach

                try {
                    val parent = (memberUI.invoke("getParent") as? UIPanelAPI) ?: return@forEach
                    val firstChild = parent.getChildrenCopy().find { it.getFieldsMatching(name = "buyButton").isNotEmpty() } as? UIPanelAPI
                        ?: return@forEach

                    val desiredButton = firstChild.getChildrenCopy().find { buttonChild ->
                        val tooltip = buttonChild.invoke("getTooltip")
                        tooltip?.getFieldsMatching(fieldAssignableTo = String::class.java)?.find {
                            val tooltipName = it.get(tooltip)
                            tooltipName == "Store"
                        } != null
                    } ?: return@forEach

                    val mouseX = Global.getSettings().mouseX
                    val mouseY = Global.getSettings().mouseY

                    val x = desiredButton.position.x
                    val y = desiredButton.position.y
                    val width = desiredButton.position.width
                    val height = desiredButton.position.height

                    if (mouseX >= x && mouseX <= x + width &&
                        mouseY >= y && mouseY <= y + height
                    ) {
                        storeOfficer(memberUI)
                    }
                } catch (e: Exception) {
                    MISC.showError("Storing the officer in cargo failed", e)
                }
            }

        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI>) {

    }

    override fun processCampaignInputPostCore(events: List<InputEventAPI>) {

    }
}