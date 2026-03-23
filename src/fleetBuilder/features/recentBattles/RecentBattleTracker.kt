package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.fleet.JSONFleet
import fleetBuilder.util.lib.ClipboardUtil


class RecentBattleTracker : BaseCampaignEventListener(false) {
    override fun reportPlayerEngagement(result: EngagementResultAPI?) {
        if (result == null) return

        val playerResult = if (result.loserResult.isPlayer) result.loserResult else result.winnerResult
        val battleIsAutoPursuit = playerResult.allEverDeployedCopy == null
        if (battleIsAutoPursuit) return

        if (!result.didPlayerWin()) return

        val battle = result.battle ?: return
        //battle.snapshotSideTwo
        //battle.nonPlayerCombined

        val fleetComp = CompressedFleet.saveFleetToCompString(
            battle.combinedTwo,
            settings = FleetSettings().apply {
                memberSettings.includeCRAndHull = false
                memberSettings.personSettings.handleRankAndPost = false
            }
        )

        // DEBUG!
        if (ModSettings.enableDebug) {
            val fleetJSON = JSONFleet.saveFleetToJson(
                battle.combinedTwo,
                settings = FleetSettings().apply {
                    memberSettings.includeCRAndHull = false
                    memberSettings.personSettings.handleRankAndPost = false
                }
            )
            val fleetUnJSON = JSONFleet.extractFleetDataFromJson(fleetJSON)
            val fleetUnCOMP = CompressedFleet.extractFleetDataFromCompString(fleetComp)
            if (fleetUnJSON != fleetUnCOMP) // If not equal, this means the logic somewhere when saving and getting the fleet to/from JSON or COMP is not correct
                DisplayMessage.showError("DEBUG: Fleet data mismatch", "DEBUG: Fleet data mismatch\n\nfleetJSON:\n${fleetJSON}\n\nfleetComp:\n${fleetComp}")
        }

        ClipboardUtil.setClipboardText(fleetComp)

        val e = 0f
    }

    override fun reportBattleFinished(primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {
        // Occurs after player exits a battle, and closes the battle over dialog (likely after looting too)
        battle ?: return
        battle.snapshotSideTwo
        battle.combinedTwo
        battle.nonPlayerCombined

        val e = 0f
    }
}