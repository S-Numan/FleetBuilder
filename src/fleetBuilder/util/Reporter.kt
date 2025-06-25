package fleetBuilder.util

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.integration.save.MakeSaveRemovable
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.VariantLib

class Reporter : RefitScreenListener, EveryFrameScript, CurrentLocationChangedListener {

    fun onApplicationLoad() {
        VariantLib.onApplicationLoad()

        LoadoutManager.loadAllDirectories()
    }

    private val officerTracker = OfficerAssignmentTracker()

    fun onGameLoad(newGame: Boolean) {
        OfficerAssignmentEvents.removeAllListeners()

        MakeSaveRemovable.onGameLoad()

        officerTracker.reset()
        officerTracker.getChangedAssignments()

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
            OfficerAssignmentEvents.notifyAll(changed)
        }
    }
}
