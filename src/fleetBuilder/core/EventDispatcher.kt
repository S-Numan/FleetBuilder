package fleetBuilder.core

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.features.cargoAutoManage.CargoAutoManager
import fleetBuilder.features.commanderShuttle.CommanderShuttle
import fleetBuilder.features.codexButton.CampaignCodexButton
import fleetBuilder.features.filters.injection.CampaignFleetScreenFilter
import fleetBuilder.features.filters.injection.CampaignModPickerFilter
import fleetBuilder.features.autofit.listener.CampaignAutofitAdder
import fleetBuilder.features.hotkeyHandler.CampaignClipboardHotkeyHandler
import fleetBuilder.features.cargoAutoManage.CargoAutoManagerOpener
import fleetBuilder.features.officerStorage.CatchStoreMemberButton
import fleetBuilder.features.removeRefitHullMod.RemoveRefitHullmod
import fleetBuilder.features.officerStorage.UnstoreOfficersInCargo
import fleetBuilder.core.makeSaveRemovable.MakeSaveRemovable
import fleetBuilder.serialization.PlayerSaveUtil
import fleetBuilder.util.listeners.ShipOfficerChangeEvents
import fleetBuilder.util.listeners.ShipOfficerChangeTracker
import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.util.VariantLib

class EventDispatcher : RefitScreenListener, EveryFrameScript, CurrentLocationChangedListener {

    companion object {
        fun setListeners() {
            val sector = Global.getSector() ?: return

            val listeners = sector.listenerManager

            // Generic helper for managing listeners
            fun <T : Any> manageListener(clazz: Class<T>, enabled: Boolean, creator: () -> T) {
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

            manageListener(CampaignClipboardHotkeyHandler::class.java, ModSettings.fleetClipboardHotkeyHandler) { CampaignClipboardHotkeyHandler() }
            manageListener(CatchStoreMemberButton::class.java, ModSettings.storeOfficersInCargo || ModSettings.unassignPlayer()) { CatchStoreMemberButton() }
            manageListener(CargoAutoManagerOpener::class.java, ModSettings.cargoAutoManager) { CargoAutoManagerOpener() }
            manageListener(RemoveRefitHullmod::class.java, ModSettings.removeRefitHullmod) { RemoveRefitHullmod() }

            manageTransientScript(CampaignAutofitAdder::class.java, ModSettings.autofitMenuEnabled) { CampaignAutofitAdder() }
            manageTransientScript(CampaignCodexButton::class.java, ModSettings.devModeCodexButtonEnabled) { CampaignCodexButton() }
            manageTransientScript(CampaignFleetScreenFilter::class.java, ModSettings.fleetScreenFilter) { CampaignFleetScreenFilter() }
            manageTransientScript(CargoAutoManager::class.java, ModSettings.cargoAutoManager) { CargoAutoManager() }
            manageTransientScript(CampaignModPickerFilter::class.java, ModSettings.modPickerFilter) { CampaignModPickerFilter() }
            manageTransientScript(UnstoreOfficersInCargo::class.java, true) { UnstoreOfficersInCargo() } // Should always be enabled
        }
    }

    fun onApplicationLoad() {
        ModSettings.onApplicationLoad()

        VariantLib.onApplicationLoad()

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
            val json = PlayerSaveUtil.createPlayerSaveJson()

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

    override fun reportCurrentLocationChanged(prev: LocationAPI, curr: LocationAPI) {
        CommanderShuttle.reportCurrentLocationChanged(prev, curr)
    }

    override fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {
        //VariantLib.reportFleetMemberVariantSaved(member, dockedAt)
    }


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