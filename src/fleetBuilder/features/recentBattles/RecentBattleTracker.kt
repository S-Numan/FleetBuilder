package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.fleet.FleetSettings


class RecentBattleTracker : BaseCampaignEventListener(false) {
    override fun reportPlayerEngagement(result: EngagementResultAPI?) {
        if (result == null) return

        val playerResult = if (result.loserResult.isPlayer) result.loserResult else result.winnerResult
        val battleIsAutoPursuit = playerResult.allEverDeployedCopy == null
        if (battleIsAutoPursuit) return

        if (!result.didPlayerWin()) return

        val battle = result.battle ?: return

        //battle.combinedTwo.id // TODO: Check if id inconsistently changes. If it doesn't, maybe use this instead?
        val id = battle.sideTwo.getOrNull(0)?.id ?: return

        try {
            FleetDirectoryService.saveFleet(battle.combinedTwo, id, settings = FleetSettings().apply {
                memberSettings.personSettings.handleRankAndPost = false
            })
        } catch (e: Exception) {
            DisplayMessage.showError("Failed to save fleet after battle", e)
        }
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