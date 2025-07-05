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
import fleetBuilder.util.MISC.getViewedFleetInSubmarket
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.getChildrenCopy
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.invoke

class StoreOfficersInCargo : EveryFrameScript, CampaignInputListener {
    var init = false

    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) return

        if (!init) {
            init = true

            /*ShipOfficerChangeEvents.addTransientListener { change ->
                if (change.current != null && change.current.memoryWithoutUpdate.contains("\$FB_stored_officer")) {
                    change.current.memoryWithoutUpdate.unset("\$FB_stored_officer")
                    change.current.memoryWithoutUpdate.unset(Misc.CAPTAIN_UNREMOVABLE)
                    Global.getSector().playerFleet.fleetData.addOfficer(change.current)
                }
            }*/
        }


        val ui = sector.campaignUI ?: return
        if (ui.getActualCurrentTab() != CoreUITabId.FLEET) return
        val playerFleet = sector.playerFleet ?: return
        if (Mouse.isButtonDown(0)) return // Don't do anything if the mouse is down. This is a hack as isLMBUpEvent does not work properly.
        playerFleet.fleetData.membersListCopy.forEach { member ->
            if (member != null && member.captain != null && member.captain.memoryWithoutUpdate.contains("\$FB_stored_officer")) {
                member.captain.memoryWithoutUpdate.unset("\$FB_stored_officer")
                member.captain.memoryWithoutUpdate.unset(Misc.CAPTAIN_UNREMOVABLE)

                playerFleet.fleetData.addOfficer(member.captain)
            }
        }
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    override fun getListenerInputPriority(): Int = 10

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) return
        val ui = sector.campaignUI ?: return
        if (ui.currentInteractionDialog == null || ui.currentInteractionDialog.interactionTarget == null || ui.currentInteractionDialog.interactionTarget.market == null) return
        if (ui.getActualCurrentTab() != CoreUITabId.FLEET) return

        val submarket = getSelectedSubmarketInFleetTab() ?: return
        if (submarket.specId != Submarkets.SUBMARKET_STORAGE || submarket.faction != sector.playerFaction || !submarket.plugin.isFreeTransfer) return //Don't sell officers to other factions
        val viewedFleet = getViewedFleetInSubmarket()
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