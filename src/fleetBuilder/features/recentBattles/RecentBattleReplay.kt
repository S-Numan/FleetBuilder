package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
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
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.deferredAction.CampaignDeferredActionPlugin
import fleetBuilder.util.deferredAction.CombatDeferredActionPlugin
import fleetBuilder.util.isIdle
import fleetBuilder.util.safeInvoke

// Initial code taken from Ship Mastery System by float

object RecentBattleReplay {

    const val isSimulatorKey = "FB_isSimulator"

    fun simulateBattle(bcc: BattleCreationContext) { //, onBackFromEngagement: () -> Unit) {
        try {
            val campUI = Global.getSector().campaignUI
            
            campUI.safeInvoke("setNextTransitionFast", true)
            val coreUI = ReflectionMisc.getCoreUI()
            //coreUI?.safeInvoke("closeCurrent")
            coreUI?.safeInvoke("dialogDismissed", coreUI, 0)

            if (!campUI.isIdle()) {
                DisplayMessage.showError("Cannot simulate battle, UI is not idle. No dialogs must be open.")
                return
            }

            //val campUI = Global.getSector().campaignUI // as CampaignState

            RecentBattleTracker.lastPlayerBattleTimestamp = Global.getSector().lastPlayerBattleTimestamp
            RecentBattleTracker.lastPlayerBattleWon = Global.getSector().isLastPlayerBattleWon

            for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                val member = fm as FleetMember
                RecentBattleTracker.savedStatuses[member] = FleetMemberStatus(member)
                RecentBattleTracker.savedCR[member] = member.repairTracker.cr
                origCaptains[member] = member.captain
            }

            val engine = CombatEngine.getInstance()
            //engine.isMissionSim = !Global.getSettings().isInCampaignState
            //AppDriver.getInstance().session["run combat simulator"] = Any()
            //campUI.startBattle(bcc)

            class PlaceholderDialog : InteractionDialogPlugin {
                override fun init(dialog: InteractionDialogAPI?) {}
                override fun optionSelected(optionText: String?, optionData: Any?) {}
                override fun optionMousedOver(optionText: String?, optionData: Any?) {}
                override fun advance(amount: Float) {}
                override fun backFromEngagement(battleResult: EngagementResultAPI?) {
                    val campUI = Global.getSector().campaignUI
                    val dialog = campUI.currentInteractionDialog
                    dialog.safeInvoke("makeOptionInstant", 0)
                    dialog.safeInvoke("dismiss", 0)

                    val engine = CombatEngine.getInstance()
                    engine.customData.remove(isSimulatorKey)

                    for ((member, captain) in origCaptains) {
                        if (captain != null) {
                            member.captain = captain
                        }
                    }

                    Global.getSector().campaignUI.set("enemyFleetForBattle", null)

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
                }

                override fun getContext(): Any? = null
                override fun getMemoryMap(): MutableMap<String, MemoryAPI> = hashMapOf()
            }
            campUI.showInteractionDialog(PlaceholderDialog(), Global.getSector().playerFleet)
            val placeholderDialog = campUI.currentInteractionDialog
            placeholderDialog!!.startBattle(bcc)

            engine.customData[isSimulatorKey] = true

            CombatDeferredActionPlugin.performOnPlayerBattleStart {
                val engine = CombatEngine.getInstance()
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
            }

        } catch (e: Exception) {
            Global.getSector()?.campaignUI?.currentInteractionDialog?.dismiss()
            Global.getLogger(this.javaClass).error("Replay battle failed: ", e)
        }
    }
}