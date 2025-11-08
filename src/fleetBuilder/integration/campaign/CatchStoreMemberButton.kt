package fleetBuilder.integration.campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.FleetMember
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings
import fleetBuilder.features.CommanderShuttle
import fleetBuilder.util.*
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import starficz.ReflectionUtils.getFieldsMatching
import starficz.getChildrenCopy

class CatchStoreMemberButton : CampaignInputListener {
    override fun getListenerInputPriority(): Int = 10

    var storePlayerDelay = false
    var lastStoreHoverStatus: FleetMemberAPI? = null

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) return
        val ui = sector.campaignUI ?: return
        if (ui.currentInteractionDialog == null || ui.currentInteractionDialog.interactionTarget == null || ui.currentInteractionDialog.interactionTarget.market == null) return
        if (ui.getActualCurrentTab() != CoreUITabId.FLEET) return

        val viewedFleet = ReflectionMisc.getViewedFleetInFleetPanel()
        if (viewedFleet !== sector.playerFleet.fleetData)//If we aren't looking at the user's fleet, don't continue
            return

        fun storeOfficer(captain: PersonAPI) {
            val submarket = ReflectionMisc.getSelectedSubmarketInFleetTab() ?: return
            if (!submarket.plugin.isFreeTransfer) return // Don't sell officers

            if (!captain.isDefault && !captain.isPlayer && !captain.memoryWithoutUpdate.contains(Misc.CAPTAIN_UNREMOVABLE)
                && captain.faction.id != "tahlan_allmother" // Mod specific support to avoid issues. Storing the Rigveda and other Lostech ships like it with the non AI, non built in yet non-removable captain causes issues.
            ) {
                captain.memoryWithoutUpdate.set(ModSettings.storedOfficerTag, true)
                captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                sector.playerFleet.fleetData.removeOfficer(captain)
            }
        }

        fun hoveringOverStore(): FleetMemberAPI? {
            val memberUI = ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel() ?: return null

            try {
                val parent = (memberUI.safeInvoke("getParent") as? UIPanelAPI) ?: return null
                val firstChild = parent.getChildrenCopy().find { it.getFieldsMatching(name = "buyButton").isNotEmpty() } as? UIPanelAPI
                    ?: return null

                val desiredButton = firstChild.getChildrenCopy().find { buttonChild ->
                    val tooltip = buttonChild.safeInvoke("getTooltip")
                    tooltip?.getFieldsMatching(fieldAssignableTo = String::class.java)?.find {
                        val tooltipName = it.get(tooltip)
                        tooltipName == "Store"
                    } != null
                } ?: return null

                if (FBMisc.isMouseHoveringOverComponent(desiredButton)) {
                    return memberUI.getFieldsMatching(type = FleetMember::class.java).getOrNull(0)?.get(memberUI) as? FleetMemberAPI
                }

            } catch (e: Exception) {
                DisplayMessage.showError(FBTxt.txt("failed_to_get_store_member_button"), e)
            }

            return null
        }

        if (storePlayerDelay) {
            val newHoverStatus = hoveringOverStore()

            if (Mouse.isButtonDown(0)) {
                if (newHoverStatus == null)
                    storePlayerDelay = false

            } else if (newHoverStatus == null && lastStoreHoverStatus != null) {
                storePlayerDelay = false
                CommanderShuttle.addPlayerShuttle()
            }

            lastStoreHoverStatus = newHoverStatus
        }

        events.forEach { event ->
            if (event.isConsumed) return@forEach

            if (event.eventType == InputEventType.KEY_DOWN && event.eventValue == Keyboard.KEY_S) {
                val memberUI = ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel() ?: return@forEach
                val mouseOverMember = memberUI.getFieldsMatching(type = FleetMember::class.java).getOrNull(0)?.get(memberUI) as? FleetMemberAPI
                    ?: return@forEach
                val captain = mouseOverMember.captain

                if (captain.isPlayer && ModSettings.unassignPlayer() && !mouseOverMember.variant.hasHullMod(ModSettings.commandShuttleId)) {
                    storePlayerDelay = true
                    lastStoreHoverStatus = mouseOverMember
                } else if (ModSettings.storeOfficersInCargo)
                    storeOfficer(captain)

            } else if (event.isLMBDownEvent) {
                val member = hoveringOverStore()
                if (member != null) {
                    if (member.captain.isPlayer && ModSettings.unassignPlayer() && !member.variant.hasHullMod(ModSettings.commandShuttleId))
                        storePlayerDelay = true
                    else if (ModSettings.storeOfficersInCargo)
                        storeOfficer(member.captain)
                }
            }

        }
    }

    override fun processCampaignInputPreFleetControl(events: List<InputEventAPI>) {

    }

    override fun processCampaignInputPostCore(events: List<InputEventAPI>) {

    }
}