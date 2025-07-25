package fleetBuilder.integration.campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import fleetBuilder.util.*
import org.lwjgl.input.Keyboard
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

        val submarket = ReflectionMisc.getSelectedSubmarketInFleetTab() ?: return
        if (submarket.faction != sector.playerFaction || !submarket.plugin.isFreeTransfer) return //Don't sell officers to other factions
        val viewedFleet = ReflectionMisc.getViewedFleetInFleetPanel()
        if (viewedFleet !== sector.playerFleet.fleetData)//If we aren't looking at the user's fleet, don't continue
            return


        fun storeOfficer(memberUI: UIPanelAPI) {
            val mouseOverMember = memberUI.getFieldsMatching(type = FleetMember::class.java).getOrNull(0)?.get(memberUI) as? FleetMemberAPI
                ?: return
            val captain = mouseOverMember.captain
            if (!captain.isDefault && !captain.isPlayer && !captain.memoryWithoutUpdate.contains(Misc.CAPTAIN_UNREMOVABLE)
                && captain.faction.id != "tahlan_allmother" // Mod specific support to avoid issues. Storing the Rigveda and other Lostech ships like it with the non AI, non built in yet non-removable captain causes issues.
            ) {
                captain.memoryWithoutUpdate.set("\$FB_stored_officer", true)
                captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                sector.playerFleet.fleetData.removeOfficer(captain)
            }
        }

        events.forEach { event ->
            if (event.isConsumed) return@forEach

            if (event.eventType == InputEventType.KEY_DOWN && event.eventValue == Keyboard.KEY_S) {
                val memberUI = ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel() ?: return@forEach
                storeOfficer(memberUI)
            } else if (event.isLMBDownEvent) {
                val memberUI = ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel() ?: return@forEach

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

                    if (FBMisc.isMouseHoveringOverComponent(desiredButton)) {
                        storeOfficer(memberUI)
                    }

                } catch (e: Exception) {
                    DisplayMessage.showError("Storing the officer in cargo failed", e)
                }
            }

        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI>) {

    }

    override fun processCampaignInputPostCore(events: List<InputEventAPI>) {

    }
}