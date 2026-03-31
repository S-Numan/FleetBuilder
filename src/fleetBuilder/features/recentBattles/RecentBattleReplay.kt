package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.campaign.fleet.FleetMemberStatus
import com.fs.starfarer.combat.CombatEngine
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.features.recentBattles.RecentBattleTracker.Companion.lastPlayerBattleTimestamp
import fleetBuilder.features.recentBattles.RecentBattleTracker.Companion.lastPlayerBattleWon
import fleetBuilder.features.recentBattles.RecentBattleTracker.Companion.origCaptains
import fleetBuilder.features.recentBattles.RecentBattleTracker.Companion.savedCR
import fleetBuilder.features.recentBattles.RecentBattleTracker.Companion.savedStatuses
import fleetBuilder.otherMods.starficz.ReflectionUtils.set
import fleetBuilder.util.deferredAction.CampaignDeferredActionPlugin

// Initial code taken from Ship Mastery System by float

object RecentBattleReplay {

    const val isSimulatorKey = "FB_isSimulator"

    fun simulateBattle(bcc: BattleCreationContext) { //, onBackFromEngagement: () -> Unit) {
        try {
            val campaignState = Global.getSector().campaignUI // as CampaignState

            campaignState.startBattle(bcc)

            RecentBattleTracker.lastPlayerBattleTimestamp = Global.getSector().lastPlayerBattleTimestamp
            RecentBattleTracker.lastPlayerBattleWon = Global.getSector().isLastPlayerBattleWon

            for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                val member = fm as FleetMember
                RecentBattleTracker.savedStatuses[member] = FleetMemberStatus(member)
                RecentBattleTracker.savedCR[member] = member.repairTracker.cr
                origCaptains[member] = member.captain
            }

            CampaignDeferredActionPlugin.performOnPlayerBattleFinish {
                if (lastPlayerBattleTimestamp != null) {
                    try {
                        for ((member, captain) in origCaptains) {
                            if (captain != null) {
                                member.captain = captain
                            }
                        }

                        for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                            val member = fm as FleetMember
                            fm.set("status", savedStatuses[member]!!)
                            member.repairTracker.cr = savedCR[member] ?: member.repairTracker.maxCR
                            member.updateStats()
                        }

                        CampaignDeferredActionPlugin.performLater(0f) {
                            Global.getSector().lastPlayerBattleTimestamp = lastPlayerBattleTimestamp!!
                            Global.getSector().isLastPlayerBattleWon = lastPlayerBattleWon!!
                        }
                    } catch (e: Exception) {
                        DisplayMessage.showError("Failed to restore fleet after simulated battle.", e)
                    }
                }
            }

            val engine = CombatEngine.getInstance()
            engine.customData[isSimulatorKey] = true

            engine.addPlugin(object : BaseEveryFrameCombatPlugin() {
                private var removedConfirm = false

                override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
                    if (engine.isEnemyInFullRetreat && !removedConfirm) {
                        removedConfirm = true
                        engine.setCustomExit(
                            "End Simulation",
                            null
                        )
                    }
                }

                override fun init(engine: CombatEngineAPI) {
                    engine.setCustomExit(
                        "End Simulation",
                        "Exit the simulation?"
                    )
                }
            })

        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Replay battle failed: ", e)
        }
    }
}