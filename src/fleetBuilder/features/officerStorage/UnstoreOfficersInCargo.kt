package fleetBuilder.features.officerStorage

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.FBConst
import fleetBuilder.core.FBSettings
import fleetBuilder.core.FBTxt
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.util.kotlin.getActualCurrentTab
import fleetBuilder.util.kotlin.getAssignedOfficers
import org.lwjgl.input.Mouse
import org.magiclib.kotlin.getMaxOfficers
import org.magiclib.kotlin.isMercenary

internal class UnstoreOfficersInCargo : EveryFrameScript {
    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return

        if (!sector.isPaused) {
            if (!FBSettings.storeOfficersInCargo) // Avoid running this logic if the setting is disabled to avoid unnecessary troubles.
                return

            val playerFleet = sector.playerFleet ?: return

            //Mothball captained ships that go above the officer limit
            if (sector.playerPerson?.memoryWithoutUpdate?.getBoolean("\$FB_NO-OVER-OFFICER-LIMIT-MOTHBALL") != true && !FBSettings.cheatsEnabled() &&
                playerFleet.fleetData.getAssignedOfficers().count { !it.isMercenary() } > playerFleet.getMaxOfficers()
            ) {
                var nonMothballedOfficerCount = getNonMothballedOfficerCount(playerFleet)
                val maxOfficers = playerFleet.getMaxOfficers()

                if (nonMothballedOfficerCount > maxOfficers) {
                    for (member in playerFleet.fleetData.membersListCopy.asReversed()) {
                        if (maxOfficers >= nonMothballedOfficerCount) break

                        val captain = member?.captain ?: continue
                        val repair = member.repairTracker

                        if (!captain.isDefault &&
                            !captain.isMercenary() &&
                            !captain.isPlayer &&
                            !captain.isAICore &&
                            !repair.isMothballed &&
                            !repair.isCrashMothballed
                        ) {
                            repair.isMothballed = true
                            nonMothballedOfficerCount--
                        }
                    }

                    sector.campaignUI.addMessage(FBTxt.txt("went_beyond_officer_limit", maxOfficers), Misc.getNegativeHighlightColor())
                }
            }

            return
        }

        //Game is paused past this point

        val ui = sector.campaignUI ?: return
        if (sector.currentlyOpenMarket == null || ui.getActualCurrentTab() != CoreUITabId.FLEET) return
        //Can only get here if in the fleet tab of a market


        if (Mouse.isButtonDown(0)) return // Don't do anything if the mouse is down. This is a hack as isLMBUpEvent does not work properly for the use-case I want to use it for
        val playerFleet = sector.playerFleet?.fleetData ?: return
        playerFleet.membersListCopy.forEach { member ->
            if (member != null && member.captain != null && member.captain.memoryWithoutUpdate.contains(FBConst.STORED_OFFICER_TAG)) {
                member.captain.memoryWithoutUpdate.unset(FBConst.STORED_OFFICER_TAG)
                member.captain.memoryWithoutUpdate.unset(Misc.CAPTAIN_UNREMOVABLE)

                if (!member.captain.isDefault && !member.captain.isAICore) {
                    playerFleet.addOfficer(member.captain)
                    if (FBSettings.storeOfficersInCargo && getNonMothballedOfficerCount(playerFleet.fleet) == playerFleet.fleet.getMaxOfficers() + 1)
                        DisplayMessage.showMessage(FBTxt.txt("officer_limit_reached"), Misc.getNegativeHighlightColor())
                }
            }
        }
    }

    private fun getNonMothballedOfficerCount(playerFleet: CampaignFleetAPI): Int {
        var nonMothballedOfficerCount = 0
        for (member in playerFleet.fleetData.membersListCopy) {
            val captain = member?.captain ?: continue
            val repair = member.repairTracker

            if (!captain.isDefault &&
                !captain.isMercenary() &&
                !captain.isPlayer &&
                !captain.isAICore &&
                !repair.isMothballed &&
                !repair.isCrashMothballed
            )
                nonMothballedOfficerCount++
        }
        return nonMothballedOfficerCount
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }
}