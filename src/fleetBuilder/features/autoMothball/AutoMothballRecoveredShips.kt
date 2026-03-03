package fleetBuilder.features.autoMothball

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FleetDataAPI
import fleetBuilder.core.displayMessage.DisplayMessage

private const val CR_THRESHOLD = 0.4f
private const val HULL_THRESHOLD = 0.4f

class AutoMothballRecoveredShips : EveryFrameScript {
    private val knownMemberIds = mutableSetOf<String>()
    private var wasAtInteraction = false
    private var wasAtMarket = false

    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true

    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return
        val fleetData = sector.playerFleet?.fleetData ?: return

        val isAtInteraction = sector.campaignUI?.currentInteractionDialog != null
        val isAtMarket = sector.currentlyOpenMarket != null

        // Just opened an interaction
        if (!wasAtInteraction && isAtInteraction) {
            snapshotFleet(fleetData)
        }

        // Just closed interaction (and not leaving market)
        if (wasAtInteraction && !isAtInteraction && !isAtMarket) {
            mothballNewlyRecoveredShips(fleetData)
        }

        wasAtInteraction = isAtInteraction
        wasAtMarket = isAtMarket
    }

    private fun snapshotFleet(fleetData: FleetDataAPI) {
        knownMemberIds.clear()
        for (member in fleetData.membersListCopy) {
            knownMemberIds.add(member.id)
        }
    }

    private fun mothballNewlyRecoveredShips(fleetData: FleetDataAPI) {
        for (member in fleetData.membersListCopy) {
            if (member.id in knownMemberIds) continue

            val tracker = member.repairTracker
            val isDamaged = tracker.cr <= CR_THRESHOLD && member.status.hullFraction <= HULL_THRESHOLD
            val notAlreadyMothballed = !tracker.isMothballed && !tracker.isCrashMothballed

            if (isDamaged && notAlreadyMothballed)
                tracker.isMothballed = true
        }
    }
}