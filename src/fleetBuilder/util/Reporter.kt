package fleetBuilder.util

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.integration.campaign.CampaignAutofitAdder
import fleetBuilder.integration.campaign.CampaignClipboardHotkeyHandler
import fleetBuilder.integration.campaign.CampaignCodexButton
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

            fun <T : Any> manageListener(clazz: Class<T>, enabled: Boolean, creator: () -> T) {
                if (enabled) {
                    if (!listeners.hasListenerOfClass(clazz))
                        listeners.addListener(creator(), true)
                } else {
                    listeners.removeListenerOfClass(clazz)
                }
            }

            manageListener(CampaignAutofitAdder::class.java, ModSettings.autofitMenuEnabled) { CampaignAutofitAdder() }
            manageListener(CampaignClipboardHotkeyHandler::class.java, ModSettings.fleetClipboardHotkeyHandler) { CampaignClipboardHotkeyHandler() }


            val codexClass = CampaignCodexButton::class.java
            if (ModSettings.devModeCodexButtonEnabled) {
                if (!sector.hasTransientScript(codexClass))
                    sector.addTransientScript(CampaignCodexButton())
            } else {
                sector.removeTransientScriptsOfClass(codexClass)
            }
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

        MISC.onGameLoad(newGame)
    }

    fun beforeGameSave() {
        MISC.beforeGameSave()

        MakeSaveRemovable.beforeGameSave()
    }

    fun afterGameSave() {
        MakeSaveRemovable.afterGameSave()

        MISC.afterGameSave()

        if (ModSettings.backupSave) {
            val json = MISC.createPlayerSaveJson()
            Global.getSettings().writeJSONToCommon("SaveTransfer/lastSave", json, false)
        }
    }

    override fun reportCurrentLocationChanged(prev: LocationAPI, curr: LocationAPI) {
        MISC.reportCurrentLocationChanged(prev, curr)
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
