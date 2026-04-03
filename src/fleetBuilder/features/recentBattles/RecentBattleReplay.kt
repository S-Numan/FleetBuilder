package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
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
import fleetBuilder.util.api.CampaignUtils
import fleetBuilder.util.deferredAction.CampaignDeferredActionPlugin
import fleetBuilder.util.deferredAction.CombatDeferredActionPlugin
import fleetBuilder.util.kotlin.safeInvoke

// Initial code taken from Ship Mastery System by float

object RecentBattleReplay {

    const val IS_SIMULATOR_KEY = "FB_isSimulator"

    fun simulateBattle(
        bcc: BattleCreationContext,
        onBackFromEngagement: () -> Unit = {}
    ) {
        try {
            var previousEncounterDialog: InteractionDialogAPI? = null
            val musicID = Global.getSoundPlayer().currentMusicId

            fun onBackFromEngagement(fromDummyDialog: Boolean) {
                val campUI = Global.getSector().campaignUI
                //val dialog = campUI.currentInteractionDialog
                //dialog.safeInvoke("makeOptionInstant", 0)
                //dialog.safeInvoke("dismiss", 0)

                val engine = CombatEngine.getInstance()
                engine.customData.remove(IS_SIMULATOR_KEY)

                for ((member, captain) in origCaptains) {
                    if (captain != null) {
                        member.captain = captain
                    }
                }


                campUI.set("enemyFleetForBattle", null)
                campUI.safeInvoke("setEncounterDialog", previousEncounterDialog)
                //campUI.restartEncounterMusic()
                if (!fromDummyDialog) {
                    previousEncounterDialog?.safeInvoke("makeOptionInstant", 0)
                    previousEncounterDialog?.safeInvoke("dismiss", 0)
                    Global.getSector().isPaused = true

                    /*campUI.safeInvoke("setNextTransitionFast", true)
                    val target = previousEncounterDialog?.interactionTarget
                    if (target != null)
                        campUI.showInteractionDialog(target)
                    campUI.safeInvoke("setNextTransitionFast", true)
                    campUI.showCoreUITab(CoreUITabId.FLEET)*/
                }

                for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                    val member = fm as FleetMember
                    fm.set("status", savedStatuses[member]!!)
                    member.repairTracker.cr = savedCR[member] ?: member.repairTracker.maxCR
                    member.updateStats()
                }

                CampaignDeferredActionPlugin.performLater {
                    Global.getSector().lastPlayerBattleTimestamp = lastPlayerBattleTimestamp!!
                    Global.getSector().isLastPlayerBattleWon = lastPlayerBattleWon!!

                    onBackFromEngagement.invoke()
                }
            }

            CampaignUtils.closeCampaignDummyDialog()
            val dummyOpen = CampaignUtils.openCampaignDummyDialog(isInteractionDialog = true) {
                onBackFromEngagement(true)
            }
            if (!dummyOpen) {
                CampaignDeferredActionPlugin.performOnPlayerBattleFinish {
                    onBackFromEngagement(false)
                }
            }

            val campUI = Global.getSector().campaignUI

            if (campUI.currentInteractionDialog == null) {
                DisplayMessage.showError("Cannot simulate battle, no interaction dialog found.")
                CampaignUtils.closeCampaignDummyDialog()
                return
            }

            previousEncounterDialog = campUI.currentInteractionDialog;

            RecentBattleTracker.lastPlayerBattleTimestamp = Global.getSector().lastPlayerBattleTimestamp
            RecentBattleTracker.lastPlayerBattleWon = Global.getSector().isLastPlayerBattleWon

            for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                val member = fm as FleetMember
                RecentBattleTracker.savedStatuses[member] = FleetMemberStatus(member)
                RecentBattleTracker.savedCR[member] = member.repairTracker.cr
                origCaptains[member] = member.captain
            }


            val engine = CombatEngine.getInstance()
            //AppDriver.getInstance().session["run combat simulator"] = Any()

            campUI.safeInvoke("setEncounterDialog", campUI.currentInteractionDialog)
            campUI!!.startBattle(bcc)

            engine.customData[IS_SIMULATOR_KEY] = true

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