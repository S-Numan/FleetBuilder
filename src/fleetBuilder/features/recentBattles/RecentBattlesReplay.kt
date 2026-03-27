package fleetBuilder.features.recentBattles

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.campaign.CampaignState
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
import org.apache.log4j.Logger

// Initial code taken from Ship Mastery System by float

object RecentBattlesReplay {

    var encounterDialogClass: Class<*>? = null
    var encounterDialogConstructor: Any? = null
    var coreUIInEncounterDialogField: Any? = null

    val logger: Logger = Logger.getLogger(RecentBattlesReplay::class.java)
    const val isSimulatorKey = "FB_isSimulator"

    /*@JvmStatic
    fun findInteractionDialogClassIfNeeded(intelUI: IntelUIAPI) {
        if (encounterDialogClass != null) return

        intelUI.showDialog(null, "dummy string")

        try {
            val coreUI = ReflectionMisc.getCoreUI()
            val childrenNonCopy = coreUI?.safeInvoke("getChildrenNonCopy") as? List<*> ?: return

            for (i in childrenNonCopy.indices.reversed()) {
                val child = childrenNonCopy[i]
                if (child is InteractionDialogAPI) {

                    val constructors = child.javaClass.getConstructorsMatching(4, parameterTypes = arrayOf())

                    child.dismiss()
                    return

                    /*for (cons in constructors) {
                        val paramTypes = ReflectionUtils.getConstructorParameterTypes(cons)

                        if (paramTypes.size == 4) {
                            encounterDialogClass = child.javaClass
                            encounterDialogConstructor = cons

                            val fields = ReflectionUtils.getFields(encounterDialogClass!!)
                            for (field in fields) {
                                val type = ReflectionUtils.getFieldType(field)
                                if (CoreUIAPI::class.java.isAssignableFrom(type)) {
                                    coreUIInEncounterDialogField = field
                                    break
                                }
                            }

                            child.dismiss()
                            return
                        }
                    }*/
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract interaction dialog", e)
        }
    }*/

    private fun makeAndSetTempBattleDialog(
        campaignState: CampaignState,
        onBackFromEngagement: () -> Unit
    ) {

        val savedStatuses = HashMap<FleetMember, FleetMemberStatus>()
        val savedCR = HashMap<FleetMember, Float>()

        val lastPlayerBattleTimestamp = Global.getSector().lastPlayerBattleTimestamp
        val lastPlayerBattleWon = Global.getSector().isLastPlayerBattleWon
        val previousEncounterDialog = campaignState.currentInteractionDialog

        val origCaptains = HashMap<FleetMemberAPI, PersonAPI?>()
        for (member in Global.getSector().playerFleet.fleetData.membersListCopy) {
            origCaptains[member] = member.captain
        }

        val battlePlugin = object : InteractionDialogPlugin {
            private fun setFleetMemberStatus(fm: FleetMember, status: FleetMemberStatus) {
                fm.set("status", status)
            }

            private fun unsetEnemyFleetForBattle(campaignState: CampaignState) {
                campaignState.set("enemyFleetForBattle", null)
            }

            override fun init(dialog: InteractionDialogAPI) {
                for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                    val member = fm as FleetMember
                    savedStatuses[member] = member.status
                    setFleetMemberStatus(member, FleetMemberStatus(member))
                    savedCR[member] = member.repairTracker.cr
                }
            }

            override fun optionSelected(optionText: String?, optionData: Any?) {}
            override fun optionMousedOver(optionText: String?, optionData: Any?) {}
            override fun advance(amount: Float) {}

            override fun backFromEngagement(battleResult: EngagementResultAPI?) {

                // Restore captains
                for ((member, captain) in origCaptains) {
                    if (captain != null) {
                        member.captain = captain
                    }
                }

                // Restore previous dialog
                /*ReflectionUtils.invokeMethodExtWithClasses(
                    campaignState,
                    "setEncounterDialog",
                    false,
                    arrayOf(encounterDialogClass),
                    previousEncounterDialog
                )*/
                //campaignState.safeInvoke("setEncounterDialog", false, arrayOf(encounterDialogClass), previousEncounterDialog)

                unsetEnemyFleetForBattle(campaignState)

                // Restore ship states
                for (fm in Global.getSector().playerFleet.fleetData.membersListCopy) {
                    val member = fm as FleetMember
                    setFleetMemberStatus(member, savedStatuses[member]!!)
                    val cr = savedCR[member]
                    member.repairTracker.cr =
                        cr ?: member.repairTracker.maxCR
                    member.updateStats()
                }

                CampaignDeferredActionPlugin.performLater(0f) {
                    Global.getSector().lastPlayerBattleTimestamp = lastPlayerBattleTimestamp
                    Global.getSector().isLastPlayerBattleWon = lastPlayerBattleWon
                    onBackFromEngagement.invoke()
                }
            }

            override fun getContext(): Any? = null
            override fun getMemoryMap(): MutableMap<String, MemoryAPI>? = null
        }

        Global.getSector().isPaused = true

        val screenPanel = ReflectionMisc.getScreenPanel()

        /*val newDialog = ReflectionUtils.invokeConstructor(
            encounterDialogConstructor!!,
            arrayOf(null, battlePlugin, screenPanel, campaignState)
        )

        if (coreUIInEncounterDialogField != null) {
            newDialog.set(coreUIInEncounterDialogField!!, ReflectionMisc.getCoreUI())
        }*/

        //campaignState.safeInvoke("setEncounterDialog", newDialog)
    }

    @JvmStatic
    fun simulateBattle(bcc: BattleCreationContext) { //, onBackFromEngagement: () -> Unit) {
        try {
            val campaignState = Global.getSector().campaignUI as CampaignState

            //makeAndSetTempBattleDialog(campaignState, onBackFromEngagement)

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
            logger.error("Replay battle failed: ", e)
        }
    }
}