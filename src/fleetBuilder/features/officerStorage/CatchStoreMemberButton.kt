package fleetBuilder.features.officerStorage

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
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.features.commanderShuttle.CommanderShuttle
import fleetBuilder.ui.UIUtils
import fleetBuilder.util.FBTxt
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.safeInvoke
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.getChildrenCopy

internal class CatchStoreMemberButton : CampaignInputListener {
    override fun getListenerInputPriority(): Int = 10

    var storePlayerDelay = false
    var lastStoreHoverStatus: FleetMemberAPI? = null

    override fun processCampaignInputPreCore(events: List<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) return
        val ui = sector.campaignUI ?: return
        if (ui.currentInteractionDialog == null || ui.currentInteractionDialog.interactionTarget == null || ui.currentInteractionDialog.interactionTarget.market == null) return
        if (ui.getActualCurrentTab() != CoreUITabId.FLEET) return
        val playerFleet = sector.playerFleet ?: return
        val playerFleetData = playerFleet.fleetData ?: return

        val viewedFleet = ReflectionMisc.getViewedFleetInFleetPanel()
        if (viewedFleet !== playerFleetData)//If we aren't looking at the user's fleet, don't continue
            return

        fun storeOfficer(captain: PersonAPI) {
            val submarket = ReflectionMisc.getSelectedSubmarket() ?: return
            if (!submarket.plugin.isFreeTransfer) return // Don't sell officers

            if (!captain.isDefault && !captain.isPlayer && !captain.memoryWithoutUpdate.contains(Misc.CAPTAIN_UNREMOVABLE)
                && captain.faction.id != "tahlan_allmother" // Mod specific support to avoid issues. Storing the Rigveda and other Lostech ships like it with the non AI, non built in yet non-removable captain causes issues.
            ) {
                captain.memoryWithoutUpdate.set(FBSettings.storedOfficerTag, true)
                captain.memoryWithoutUpdate.set(Misc.CAPTAIN_UNREMOVABLE, true)
                playerFleetData.removeOfficer(captain)
            }
        }

        fun hoveringOverStore(): FleetMemberAPI? {
            val memberUI = ReflectionMisc.getMemberUIHoveredInFleetTabLowerPanel() ?: return null

            try {
                val parent = (memberUI.safeInvoke("getParent") as? UIPanelAPI) ?: return null
                val firstChild = parent.getChildrenCopy().find { it.getFieldsMatching(name = "buyButton").isNotEmpty() } as? UIPanelAPI
                    ?: return null

                // Get the highest button. (Store button)
                val desiredButton = firstChild.getChildrenCopy()
                    .maxByOrNull { child ->
                        child.position.y
                    } ?: return null

                if (UIUtils.isMouseHoveringOverComponent(desiredButton)) {
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

                if (captain.isPlayer && FBSettings.unassignPlayer() && !mouseOverMember.variant.hasHullMod(FBSettings.commandShuttleId)) {
                    storePlayerDelay = true
                    lastStoreHoverStatus = mouseOverMember
                } else if (FBSettings.storeOfficersInCargo)
                    storeOfficer(captain)

            } else if (event.isLMBDownEvent) {
                val member = hoveringOverStore()
                if (member != null) {
                    if (member.captain.isPlayer && FBSettings.unassignPlayer() && !member.variant.hasHullMod(FBSettings.commandShuttleId))
                        storePlayerDelay = true
                    else if (FBSettings.storeOfficersInCargo)
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