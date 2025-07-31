package fleetBuilder.util

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.features.CommanderShuttle
import fleetBuilder.integration.campaign.*
import fleetBuilder.integration.save.MakeSaveRemovable
import fleetBuilder.util.listeners.ShipOfficerChangeEvents
import fleetBuilder.util.listeners.ShipOfficerChangeTracker
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.VariantLib

class Reporter : RefitScreenListener, EveryFrameScript, CurrentLocationChangedListener {

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


            manageListener(CampaignAutofitAdder::class.java, ModSettings.autofitMenuEnabled) { CampaignAutofitAdder() }
            manageListener(CampaignClipboardHotkeyHandler::class.java, ModSettings.fleetClipboardHotkeyHandler) { CampaignClipboardHotkeyHandler() }
            manageListener(StoreOfficersInCargo::class.java, ModSettings.storeOfficersInCargo) { StoreOfficersInCargo() }

            manageTransientScript(CampaignCodexButton::class.java, ModSettings.devModeCodexButtonEnabled) { CampaignCodexButton() }
            manageTransientScript(CampaignFleetScreenFilter::class.java, ModSettings.fleetScreenFilter) { CampaignFleetScreenFilter() }
            manageTransientScript(UnstoreOfficersInCargo::class.java, true) { UnstoreOfficersInCargo() } // Should always be enabled
        }
    }

    fun onApplicationLoad() {
        ModSettings.onApplicationLoad()

        VariantLib.onApplicationLoad()

        LoadoutManager.loadAllDirectories()
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
            val json = FBMisc.createPlayerSaveJson()
            Global.getSettings().writeJSONToCommon("${ModSettings.PRIMARYDIR}/SaveTransfer/lastSave", json, false)
        }
    }

    override fun reportCurrentLocationChanged(prev: LocationAPI, curr: LocationAPI) {
        CommanderShuttle.reportCurrentLocationChanged(prev, curr)
    }

    override fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {
        VariantLib.reportFleetMemberVariantSaved(member, dockedAt)
    }


    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true


    override fun advance(amount: Float) {
        if (Global.getSector().isPaused) {
            val changed = officerTracker.getChangedAssignments()
            ShipOfficerChangeEvents.notifyAll(changed)
        }
    }
}
