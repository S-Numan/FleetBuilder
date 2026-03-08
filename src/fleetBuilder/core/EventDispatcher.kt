package fleetBuilder.core

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
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
import fleetBuilder.features.consoleMirror.LogMessageAppender
import fleetBuilder.features.filters.injection.CampaignCargoScreenFilter
import fleetBuilder.features.filters.injection.CampaignFleetScreenFilter
import fleetBuilder.features.filters.injection.CampaignModPickerFilter
import fleetBuilder.features.hotkeyHandler.CampaignClipboardHotkeyHandler
import fleetBuilder.features.officerStorage.CatchStoreMemberButton
import fleetBuilder.features.officerStorage.UnstoreOfficersInCargo
import fleetBuilder.features.removeRefitHullMod.RemoveRefitHullmod
import fleetBuilder.features.transponderOff.TransponderOff
import fleetBuilder.serialization.PlayerSaveUtils
import fleetBuilder.util.LookupUtil
import fleetBuilder.util.listeners.ShipOfficerChangeEvents
import fleetBuilder.util.listeners.ShipOfficerChangeTracker
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.lazywizard.console.Console


class EventDispatcher : EveryFrameScript {

    companion object {
        fun setListeners() {
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

            val listenerManager = sector.listenerManager
            fun <T : Any> manageCustomTransientListener(clazz: Class<T>, enabled: Boolean, creator: () -> T) {
                if (enabled) {
                    if (!listenerManager.hasListenerOfClass(clazz)) {
                        listeners.addListener(creator(), true)
                    }
                } else {
                    listenerManager.removeListenerOfClass(clazz)
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
            manageTransientScript(CampaignCargoScreenFilter::class.java, ModSettings.cargoScreenFilter) { CampaignCargoScreenFilter() }
            manageCustomTransientListener(CampaignCargoScreenFilter::class.java, ModSettings.cargoScreenFilter) { CampaignCargoScreenFilter() }
            manageTransientScript(AutoMothballRecoveredShips::class.java, ModSettings.autoMothballRecoveredShips) { AutoMothballRecoveredShips() }
            manageTransientScript(UnstoreOfficersInCargo::class.java, true) { UnstoreOfficersInCargo() } // Should always be enabled
            manageCustomTransientListener(CommanderShuttle::class.java, true) { CommanderShuttle() } // Should always be enabled
            manageTransientScript(DrawMessageOnTop::class.java, true) { DrawMessageOnTop() } // Should always be enabled

            manageCustomTransientListener(TransponderOff::class.java, ModSettings.transponderOffInHyperspace) { TransponderOff() }
        }
    }

    fun onDevModeF8Reload() {
        onApplicationLoad()
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

            Logger.getRootLogger().addAppender(LogMessageAppender())
        }

        LookupUtil.onApplicationLoad()

        ShipDirectoryService.loadAllDirectories()
    }

    private val officerTracker = ShipOfficerChangeTracker()

    fun onGameLoad(newGame: Boolean) {

        setListeners()


        ShipOfficerChangeEvents.clearAll()

        MakeSaveRemovable.onGameLoad()

        officerTracker.reset()

        CommanderShuttle.onGameLoad(newGame)
    }

    fun beforeGameSave() {
        CommanderShuttle.beforeGameSave()

        MakeSaveRemovable.beforeGameSave()
    }

    fun afterGameSave() {
        MakeSaveRemovable.afterGameSave()

        CommanderShuttle.afterGameSave()

        if (ModSettings.backupSave) {
            val json = PlayerSaveUtils.createPlayerSaveJson()

            val jsonString = json.toString(4)

            //Safety
            if (jsonString.length < 1000000) { // Starsector cannot save files over 1MB
                try {
                    Global.getSettings().writeTextFileToCommon("${ModSettings.PRIMARYDIR}/SaveTransfer/lastSave", jsonString)
                } catch (e: Exception) {
                    Global.getLogger(this.javaClass).error("FleetBuilder: Backup Save failed.")
                    //DisplayMessage.showError("FleetBuilder: Backup Save failed.", e)
                }
            } else {
                Global.getLogger(this.javaClass).warn("FleetBuilder: Backup Save is too large. Please make a SaveTransfer of your save and send it to the mod author.")
                //DisplayMessage.showMessage("FleetBuilder: Backup Save is too large. Please make a SaveTransfer of your save and send it to the mod author.", Color.YELLOW)
            }
        }
    }

    //override fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {
    //VariantLib.reportFleetMemberVariantSaved(member, dockedAt)
    //}


    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true

    private var lastDevMode: Boolean = false
    override fun advance(amount: Float) {
        if (Global.getSector().isPaused) {
            val changed = officerTracker.getChangedAssignments()
            ShipOfficerChangeEvents.notifyAll(changed)
        }

        //Detect DevMode change
        val currentDevMode = Global.getSettings().isDevMode
        if (lastDevMode != currentDevMode) {
            setListeners()

            lastDevMode = currentDevMode
        }
    }
}