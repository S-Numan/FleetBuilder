package fleetBuilder.core.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.core.displayMessage.DrawMessageOnTop
import fleetBuilder.core.makeSaveRemovable.MakeSaveRemovable
import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.features.autoMothball.AutoMothballRecoveredShips
import fleetBuilder.features.autofit.listener.CampaignAutofitAdder
import fleetBuilder.features.autofit.listener.CodexAutofitButton
import fleetBuilder.features.cargoAutoManage.CargoAutoManager
import fleetBuilder.features.cargoAutoManage.CargoAutoManagerOpener
import fleetBuilder.features.codexButton.CampaignDevModeCodexButton
import fleetBuilder.features.commanderShuttle.CommanderShuttle
import fleetBuilder.features.filters.injection.CampaignCargoScreenFilter
import fleetBuilder.features.filters.injection.CampaignFleetScreenFilter
import fleetBuilder.features.filters.injection.CampaignModPickerFilter
import fleetBuilder.features.hotkeyHandler.CampaignClipboardHotkeyHandler
import fleetBuilder.features.logMessageAppender.LogMessageAppender
import fleetBuilder.features.officerStorage.CatchStoreMemberButton
import fleetBuilder.features.officerStorage.UnstoreOfficersInCargo
import fleetBuilder.features.removeRefitHullMod.RemoveRefitHullmod
import fleetBuilder.features.transponderOff.TransponderOff
import fleetBuilder.serialization.PlayerSaveUtils
import fleetBuilder.util.LookupUtil
import fleetBuilder.util.listeners.MemberChangeEvents
import fleetBuilder.util.listeners.MemberChangeTracker
import fleetBuilder.util.listeners.OfficerChangeEvents
import fleetBuilder.util.listeners.OfficerChangeTracker
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.lazywizard.console.Console

internal class EventDispatcher : EveryFrameScript {
    companion object {
        fun setSectorListeners() {
            val sector = Global.getSector() ?: return

            val listeners = sector.listenerManager

            // Generic helper for managing listeners
            fun <T : Any> manageTransientListener(clazz: Class<T>, enabled: Boolean, creator: () -> T) {
                if (enabled) {
                    if (!listeners.hasListenerOfClass(clazz)) {
                        listeners.addListener(creator(), true)
                    }
                } else {
                    listeners.removeListenerOfClass(clazz)
                }
            }

            // Generic helper for managing transient scripts
            fun <T : EveryFrameScript> manageTransientScript(clazz: Class<T>, enabled: Boolean, creator: () -> T) {
                if (enabled) {
                    if (!sector.hasTransientScript(clazz)) {
                        sector.addTransientScript(creator())
                    }
                } else {
                    sector.removeTransientScriptsOfClass(clazz)
                }
            }

            manageTransientListener(CampaignClipboardHotkeyHandler::class.java, ModSettings.fleetClipboardHotkeyHandler) { CampaignClipboardHotkeyHandler() }
            manageTransientListener(CatchStoreMemberButton::class.java, ModSettings.storeOfficersInCargo || ModSettings.unassignPlayer()) { CatchStoreMemberButton() }
            manageTransientListener(CargoAutoManagerOpener::class.java, ModSettings.cargoAutoManager) { CargoAutoManagerOpener() }
            manageTransientListener(RemoveRefitHullmod::class.java, ModSettings.removeRefitHullmod) { RemoveRefitHullmod() }

            manageTransientScript(CampaignAutofitAdder::class.java, ModSettings.autofitMenuEnabled) { CampaignAutofitAdder() }
            manageTransientScript(CodexAutofitButton::class.java, ModSettings.autofitMenuEnabled && ModSettings.codexAutofitButton) { CodexAutofitButton() }
            manageTransientScript(CampaignDevModeCodexButton::class.java, ModSettings.devModeCodexButtonEnabled) { CampaignDevModeCodexButton() }
            manageTransientScript(CampaignFleetScreenFilter::class.java, ModSettings.fleetScreenFilter) { CampaignFleetScreenFilter() }
            manageTransientScript(CargoAutoManager::class.java, ModSettings.cargoAutoManager) { CargoAutoManager() }
            manageTransientScript(CampaignModPickerFilter::class.java, ModSettings.modPickerFilter) { CampaignModPickerFilter() }
            val cargoScreenFilter = CampaignCargoScreenFilter()
            manageTransientScript(CampaignCargoScreenFilter::class.java, ModSettings.cargoScreenFilter) { cargoScreenFilter }
            manageTransientListener(CampaignCargoScreenFilter::class.java, ModSettings.cargoScreenFilter) { cargoScreenFilter }
            manageTransientScript(AutoMothballRecoveredShips::class.java, ModSettings.autoMothballRecoveredShips) { AutoMothballRecoveredShips() }
            manageTransientScript(UnstoreOfficersInCargo::class.java, true) { UnstoreOfficersInCargo() } // Should always be enabled
            manageTransientListener(CommanderShuttle::class.java, true) { CommanderShuttle() } // Should always be enabled
            manageTransientScript(DrawMessageOnTop::class.java, true) { DrawMessageOnTop() } // Should always be enabled

            manageTransientListener(TransponderOff::class.java, ModSettings.transponderOffInHyperspace) { TransponderOff() }
        }

        fun onDevModeF8Reload() {
            onApplicationLoad()
            setSectorListeners()
        }

        fun onApplicationLoad() {
            ModSettings.onApplicationLoad()

            if (ModSettings.addLogsToConsoleModConsoleLevel != Level.OFF || ModSettings.addLogsToDisplayMessageLevel != Level.OFF) {
                // Cause the lazy class loader to load these classes preemptively to prevent issues.
                try {
                    Class.forName("org.apache.log4j.Layout")
                    Class.forName("org.apache.log4j.spi.LoggingEvent")
                    Class.forName("org.apache.log4j.Priority")
                    if (ModSettings.isConsoleModEnabled)
                        Class.forName(Console::class.java.name)
                } catch (e: ClassNotFoundException) {
                    e.printStackTrace()
                }

                val rootLogger = Logger.getRootLogger()

                if (rootLogger.getAppender("LogMessageAppender") == null) {
                    val appender = LogMessageAppender().apply { name = "LogMessageAppender" }
                    rootLogger.addAppender(appender)
                }
            }

            LookupUtil.onApplicationLoad()

            ShipDirectoryService.loadAllDirectories()
        }

        private val officerTracker = OfficerChangeTracker()
        private val memberTracker = MemberChangeTracker()

        val eventDispatcher = EventDispatcher()

        fun onGameLoad(newGame: Boolean) {
            val sector = Global.getSector() ?: run {
                DisplayMessage.showError("What?"); return
            }

            sector.addTransientScript(eventDispatcher)

            DrawMessageOnTop.Companion.onGameLoad()

            setSectorListeners()

            OfficerChangeEvents.clearAll()

            MakeSaveRemovable.onGameLoad()

            officerTracker.reset()
            memberTracker.reset()

            CommanderShuttle.Companion.onGameLoad(newGame)
        }

        fun beforeGameSave() {
            CommanderShuttle.Companion.beforeGameSave()

            MakeSaveRemovable.beforeGameSave()
        }

        fun backupSave() {
            if (ModSettings.backupSave) {
                try {
                    val compSave = PlayerSaveUtils.createSaveJson(superCompressSave = true)

                    if (compSave.length < 1000000) // Starsector cannot save files over 1MB
                        Global.getSettings().writeTextFileToCommon("${ModSettings.PRIMARYDIR}/SaveTransfer/lastSave", compSave)
                    else
                        Global.getLogger(this::class.java).warn("FleetBuilder: Backup Save is too large. Please make a SaveTransfer of your save and send it to the mod author.")

                } catch (e: Exception) {
                    Global.getLogger(this::class.java).error("FleetBuilder: Backup Save failed.", e)
                }
            }
        }

        fun onGameSaveFailed() {
            backupSave()
        }

        fun afterGameSave() {
            MakeSaveRemovable.afterGameSave()

            CommanderShuttle.Companion.afterGameSave()

            backupSave()
        }
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true

    private var lastDevMode: Boolean = false

    override fun advance(amount: Float) {
        if (Global.getSector().isPaused) {
            val officerChanges = officerTracker.getChangedAssignments()
            OfficerChangeEvents.notifyAll(officerChanges)

            val memberChanges = memberTracker.getChangedMembers()
            MemberChangeEvents.notifyAll(memberChanges)
        }

        //Detect DevMode change
        val currentDevMode = Global.getSettings().isDevMode
        if (lastDevMode != currentDevMode) {
            setSectorListeners()

            lastDevMode = currentDevMode
        }
    }
}