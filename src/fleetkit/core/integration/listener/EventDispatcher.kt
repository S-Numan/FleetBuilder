package fleetkit.core.integration.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEventListener
import fleetkit.core.config.FTSettings
import fleetkit.core.integration.feature.RemoveNullGoalVariants.removeNullGoalVariants
import fleetkit.core.integration.plugin.FTCampaignPlugin
import fleetkit.core.util.FTTxt
import fleetkit.core.util.save.backup.SaveBackupManager
import fleetkit.core.util.save.removable.MakeSaveRemovable
import fleetkit.features.autoMothball.AutoMothballRecoveredShips
import fleetkit.features.autofit.listener.CampaignAutofitAdder
import fleetkit.features.autofit.listener.CodexAutofitButton
import fleetkit.features.autofit.shipDirectory.ShipDirectoryService
import fleetkit.features.cargoAutoManage.CargoAutoManager
import fleetkit.features.cargoAutoManage.CargoAutoManagerOpener
import fleetkit.features.codexButton.CampaignDevModeCodexButton
import fleetkit.features.commanderShuttle.CommanderShuttle
import fleetkit.features.displayRecoveryEarly.DisplayDerelictRecoveryEarly
import fleetkit.features.filters.injection.CampaignCargoScreenFilter
import fleetkit.features.filters.injection.CampaignFleetScreenFilter
import fleetkit.features.filters.injection.CampaignModPickerFilter
import fleetkit.features.hotkeyHandler.CampaignClipboardHotkeyHandler
import fleetkit.features.officerStorage.CatchStoreMemberButton
import fleetkit.features.officerStorage.UnstoreOfficersInCargo
import fleetkit.features.recentBattles.RecentBattleTracker
import fleetkit.features.recentBattles.fleetDirectory.RBFleetDirectoryService
import fleetkit.features.removeOldIntelUpdates.RemoveOldIntelUpdates
import fleetkit.features.removeRefitHullMod.RemoveRefitHullmod
import fleetkit.features.transponderOff.TransponderOff
import fleetkit.ui.customPanel.DialogUtils
import fleetkit.util.listeners.MemberChangeEvents
import fleetkit.util.listeners.MemberChangeTracker
import fleetkit.util.listeners.OfficerChangeEvents
import fleetkit.util.listeners.OfficerChangeTracker
import org.lazywizard.console.Console
import org.magiclib.util.MagicIncompatibleHullmods
import org.magiclib.util.taskscheduler.CombatTaskScheduler
import org.magiclib.util.taskscheduler.SectorTaskScheduler

internal class EventDispatcher : EveryFrameScript {
    companion object {

        // EveryFrameScript
        fun <T : EveryFrameScript> manageTransientScript(
            clazz: Class<T>, enabled: Boolean = true, creator: (() -> T)? = null
        ) {
            val sector = Global.getSector() ?: return

            if (enabled) {
                if (!sector.hasTransientScript(clazz) && creator != null) {
                    sector.addTransientScript(creator())
                }
            } else {
                sector.removeTransientScriptsOfClass(clazz)
            }
        }

        // CampaignEventListener
        fun <T : CampaignEventListener> manageTransientCampaignListener(
            clazz: Class<T>, enabled: Boolean = true, creator: (() -> T)? = null
        ) {
            val sector = Global.getSector() ?: return

            if (enabled) {
                if (sector.allListeners.none { it.javaClass == clazz } && creator != null) {
                    sector.addTransientListener(creator())
                }
            } else {
                sector.allListeners
                    .filter { it.javaClass == clazz }
                    .forEach { sector.removeListener(it) }
            }
        }

        // Everything else
        fun <T : Any> manageTransientListener(
            clazz: Class<T>, enabled: Boolean = true, creator: (() -> T)? = null
        ) {
            val sector = Global.getSector() ?: return
            val listeners = sector.listenerManager ?: return

            if (enabled) {
                if (!listeners.hasListenerOfClass(clazz) && creator != null) {
                    listeners.addListener(creator(), true)
                }
            } else {
                listeners.removeListenerOfClass(clazz)
            }
        }

        fun setSectorListeners() {
            manageTransientListener(CampaignClipboardHotkeyHandler::class.java, FTSettings.fleetClipboardHotkeyHandler) { CampaignClipboardHotkeyHandler() }
            manageTransientListener(CatchStoreMemberButton::class.java, FTSettings.storeOfficersInCargo || FTSettings.unassignPlayer()) { CatchStoreMemberButton() }
            manageTransientListener(CargoAutoManagerOpener::class.java, FTSettings.cargoAutoManager) { CargoAutoManagerOpener() }
            manageTransientListener(RemoveRefitHullmod::class.java, FTSettings.removeRefitHullmod) { RemoveRefitHullmod() }

            manageTransientScript(CampaignAutofitAdder::class.java, FTSettings.autofitMenuEnabled) { CampaignAutofitAdder() }
            manageTransientScript(CodexAutofitButton::class.java, FTSettings.autofitMenuEnabled && FTSettings.codexAutofitButton) { CodexAutofitButton() }
            manageTransientScript(CampaignDevModeCodexButton::class.java, FTSettings.devModeCodexButtonEnabled) { CampaignDevModeCodexButton() }
            manageTransientScript(CampaignFleetScreenFilter::class.java, FTSettings.fleetScreenFilter) { CampaignFleetScreenFilter() }
            manageTransientScript(CargoAutoManager::class.java, FTSettings.cargoAutoManager) { CargoAutoManager() }
            manageTransientScript(CampaignModPickerFilter::class.java, FTSettings.modPickerFilter) { CampaignModPickerFilter() }

            val cargoScreenFilter = CampaignCargoScreenFilter()
            manageTransientScript(CampaignCargoScreenFilter::class.java, FTSettings.cargoScreenFilter) { cargoScreenFilter }
            manageTransientListener(CampaignCargoScreenFilter::class.java, FTSettings.cargoScreenFilter) { cargoScreenFilter }

            manageTransientScript(AutoMothballRecoveredShips::class.java, FTSettings.autoMothballRecoveredShips) { AutoMothballRecoveredShips() }
            manageTransientScript(RemoveOldIntelUpdates::class.java, FTSettings.removeOldIntelUpdates) { RemoveOldIntelUpdates() }
            manageTransientScript(DisplayDerelictRecoveryEarly::class.java, FTSettings.displayDerelictRecoveryEarly) { DisplayDerelictRecoveryEarly() }
            manageTransientListener(TransponderOff::class.java, FTSettings.transponderOffInHyperspace) { TransponderOff() }

            manageTransientCampaignListener(RecentBattleTracker::class.java, FTSettings.recentBattleTracker) { RecentBattleTracker() }

            manageTransientScript(UnstoreOfficersInCargo::class.java, true) { UnstoreOfficersInCargo() } // Should always be enabled
            manageTransientScript(DrawOnTop::class.java, true) { DrawOnTop() } // Should always be enabled
            manageTransientScript(DialogUtils::class.java, true) { DialogUtils() } // Should always be enabled
        }

        fun onDevModeF8Reload() {
            Global.getLogger(this::class.java).info("F8 Reload")

            FTTxt.setup()

            updateApplicationState()
        }

        fun onApplicationLoad() {
            Global.getLogger(this::class.java).info("onApplicationLoad")

            FTTxt.setup()

            FTSettings.onApplicationLoad()

            updateApplicationState()

            if (FTSettings.enableDebug) {
                Global.getSettings().allHullModSpecs.forEach { // TODO: remove later. This is testing to assure that wrapping the class does not break something else by accident
                    MagicIncompatibleHullmods.addIncompatibility(it.id, "debug")
                }

                CombatTaskScheduler.performOnPlayerBattleStartPersistent {
                    Console.showMessage("battle start persistent")
                }

                SectorTaskScheduler.performAfterSectorExit(true) {
                    Console.showMessage("after sector exit")
                }
            }
        }

        fun updateApplicationState() {

            if (FTSettings.autofitMenuEnabled)
                ShipDirectoryService.loadAllDirectories()
            if (FTSettings.recentBattleTracker)
                RBFleetDirectoryService.loadDirectory()

            if (Global.getCurrentState() == GameState.CAMPAIGN)
                setSectorListeners()
        }

        private val officerTracker = OfficerChangeTracker()
        private val memberTracker = MemberChangeTracker()

        val eventDispatcher = EventDispatcher()

        fun onGameLoad(newGame: Boolean) {
            Global.getLogger(this::class.java).info("onGameLoad")

            val sector = Global.getSector() ?: run {
                throw Error("How was sector null here?")
            }

            sector.addTransientScript(eventDispatcher)

            DrawOnTop.onGameLoad()

            setSectorListeners()

            OfficerChangeEvents.clearAll()

            MakeSaveRemovable.onGameLoad()

            officerTracker.reset()
            memberTracker.reset()

            CommanderShuttle.onGameLoad(newGame)

            sector.registerPlugin(FTCampaignPlugin())

            removeNullGoalVariants()

            if (FTSettings.enableDebug) {
                CombatTaskScheduler.performOnPlayerBattleStart(true) {
                    Console.showMessage("battle start non-persistent")
                }

                SectorTaskScheduler.performAfterPlayerBattle(true) {
                    Console.showMessage("after player battle")
                }
            }
        }

        fun beforeGameSave() {
            // This would be a very bad place for the game to crash, so try to catch the crash even if it isn't needed.
            try {
                MakeSaveRemovable.beforeGameSave()
            } catch (e: Exception) {
                Global.getLogger(this::class.java).error("Error in beforeGameSave", e)
            }
        }

        fun onGameSaveFailed() {
            if (FTSettings.backupSave)
                SaveBackupManager.createBackup()
        }

        fun afterGameSave() {
            MakeSaveRemovable.afterGameSave()

            if (FTSettings.backupSave)
                SaveBackupManager.createBackup()
        }
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true

    private var lastDevMode: Boolean = false

    override fun advance(amount: Float) {
        if (Global.getSector()!!.isPaused) {
            val officerChanges = officerTracker.getChangedAssignments()
            OfficerChangeEvents.notify(officerChanges)

            val memberChanges = memberTracker.getChangedMembers()
            MemberChangeEvents.notify(memberChanges)
        }

        //Detect DevMode change
        val currentDevMode = Global.getSettings().isDevMode
        if (lastDevMode != currentDevMode) {
            updateApplicationState()

            lastDevMode = currentDevMode
        }
    }
}