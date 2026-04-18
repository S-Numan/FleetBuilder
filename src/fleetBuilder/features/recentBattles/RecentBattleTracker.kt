package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.campaign.BaseCampaignEventListener
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.campaign.fleet.FleetMemberStatus
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.features.recentBattles.fleetDirectory.FleetDirectoryService
import fleetBuilder.serialization.fleet.FleetSettings


class RecentBattleTracker : BaseCampaignEventListener(false) {
    companion object {
        var lastPlayerBattleTimestamp: Long? = null
        var lastPlayerBattleWon: Boolean? = null

        var origCaptains = HashMap<FleetMemberAPI, PersonAPI?>()
        val savedStatuses = HashMap<FleetMember, FleetMemberStatus>()
        val savedCR = HashMap<FleetMember, Float>()
    }

    override fun reportPlayerEngagement(result: EngagementResultAPI?) {
        if (result == null) return

        val playerResult = if (result.loserResult.isPlayer) result.loserResult else result.winnerResult
        val battleIsAutoPursuit = playerResult.allEverDeployedCopy == null
        if (battleIsAutoPursuit) return

        if (!result.didPlayerWin()) return

        val battle = result.battle ?: return

        val id = battle.sideTwo.getOrNull(0)?.id ?: return

        val battledFleet = battle.combinedTwo

        val additionalMemKeys = mutableMapOf<String, Any?>()
        if (battledFleet.memoryWithoutUpdate["\$fleetType"] == "personBounty")
            additionalMemKeys["\$fleetType"] = "personBounty"
        if (battledFleet.memoryWithoutUpdate["\$MagicLib_Bounty_target_fleet"] == true)
            additionalMemKeys["\$MagicLib_Bounty_target_fleet"] = true

        try {
            FleetDirectoryService.getDirectory()?.addFleet(
                battledFleet, setFleetID = id, settings = FleetSettings().apply {
                    memberSettings.includeCR = false
                    memberSettings.includeHullFraction = false
                    memberSettings.personSettings.handleRankAndPost = false
                }, additionalMemKeysToAdd = additionalMemKeys
            )
        } catch (e: Exception) {
            DisplayMessage.showError("Failed to save fleet after battle", e)
        }
    }
}