package fleetBuilder.integration.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.util.Misc
import fleetBuilder.config.ModSettings
import fleetBuilder.util.getActualCurrentTab
import org.lwjgl.input.Mouse

class UnstoreOfficersInCargo : EveryFrameScript {
    //var init = false

    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) return

        /*if (!init) {
            init = true
            ShipOfficerChangeEvents.addTransientListener { change ->
                if (change.current != null && change.current.memoryWithoutUpdate.contains(ModSettings.storedOfficerTag)) {
                    change.current.memoryWithoutUpdate.unset(ModSettings.storedOfficerTag)
                    change.current.memoryWithoutUpdate.unset(Misc.CAPTAIN_UNREMOVABLE)
                    Global.getSector().playerFleet.fleetData.addOfficer(change.current)
                }
            }
        }*/

        val ui = sector.campaignUI ?: return
        if (ui.currentInteractionDialog == null || ui.currentInteractionDialog.interactionTarget == null || ui.currentInteractionDialog.interactionTarget.market == null) return // Interacting with a market
        if (ui.getActualCurrentTab() != CoreUITabId.FLEET) return // In fleet screen
        val playerFleet = sector.playerFleet ?: return
        //Can only get here if in the fleet tab of a market

        if (Mouse.isButtonDown(0)) return // Don't do anything if the mouse is down. This is a hack as isLMBUpEvent does not work properly for the use-case I want to use it for
        playerFleet.fleetData.membersListCopy.forEach { member ->
            if (member != null && member.captain != null && member.captain.memoryWithoutUpdate.contains(ModSettings.storedOfficerTag)) {
                member.captain.memoryWithoutUpdate.unset(ModSettings.storedOfficerTag)
                member.captain.memoryWithoutUpdate.unset(Misc.CAPTAIN_UNREMOVABLE)

                if (!member.captain.isDefault && !member.captain.isAICore)
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
}