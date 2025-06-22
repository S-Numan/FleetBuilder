package fleetBuilder.util

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.campaign.listeners.RefitScreenListener
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import fleetBuilder.config.ModSettings
import fleetBuilder.integration.save.MakeSaveRemoveable
import fleetBuilder.variants.LoadoutManager
import fleetBuilder.variants.VariantLib

class Reporter : RefitScreenListener, EveryFrameScript, CurrentLocationChangedListener {

    companion object {
        fun onApplicationLoad() {
            VariantLib.onApplicationLoad()

            LoadoutManager.loadAllDirectories()
        }
    }

    private val officerTracker = OfficerAssignmentTracker()

    override fun reportCurrentLocationChanged(prev: LocationAPI, curr: LocationAPI) {
        MISC.reportCurrentLocationChanged(prev, curr)
    }

    fun onGameLoad(newGame: Boolean) {
        MakeSaveRemoveable.onGameLoad()

        officerTracker.reset()
        officerTracker.getChangedAssignments()

        MISC.onGameLoad(newGame)
    }

    fun beforeGameSave() {
        MISC.beforeGameSave()

        MakeSaveRemoveable.beforeGameSave()
    }

    fun afterGameSave() {
        MakeSaveRemoveable.afterGameSave()

        MISC.afterGameSave()

        if(ModSettings.backupSave) {
            val json = MISC.createPlayerSaveJson()
            Global.getSettings().writeJSONToCommon("SaveTransfer/lastSave", json, false)
        }
    }

    override fun reportFleetMemberVariantSaved(member: FleetMemberAPI, dockedAt: MarketAPI?) {
        VariantLib.reportFleetMemberVariantSaved(member, dockedAt)
    }


    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true


    override fun advance(amount: Float) {
        if(Global.getSector().isPaused) {
            val changed = officerTracker.getChangedAssignments()
            //TODO, make a proper listener for this sort of thing. Let's other people use it too.
            for (change in changed) {
                //CampaignEngine.getInstance().campaignUI.messageDisplay.addMessage( "Officer changed on ${change.member.shipName}. TEMP, REMOVE LATER", Color.RED )
                MISC.onOfficerChange(change)
            }
        }
    }

}


class OfficerAssignmentTracker {

    data class OfficerChange(
        val member: FleetMemberAPI,
        val previous: PersonAPI?,
        val current: PersonAPI?
    )

    private val lastAssignments = mutableMapOf<String, PersonAPI?>()

    private val changed = mutableListOf<OfficerChange>()


    fun getChangedAssignments(): List<OfficerChange> {
        val fleet = Global.getSector().playerFleet ?: return emptyList()
        changed.clear()

        for (member in fleet.fleetData.membersListCopy) {
            val current = member.captain
            val previous = lastAssignments[member.id]

            if (previous !== current) {
                val currentIsDefault = current?.isDefault != false
                val previousIsDefault = previous?.isDefault != false

                if (!(currentIsDefault && previousIsDefault)) {
                    changed.add(OfficerChange(member, previous, current))
                }
            }

            lastAssignments[member.id] = current
        }

        val currentIds = HashSet<String>(fleet.fleetData.membersListCopy.size)
        for (member in fleet.fleetData.membersListCopy) {
            currentIds.add(member.id)
        }
        lastAssignments.keys.retainAll(currentIds)

        return changed
    }

    fun reset() {
        lastAssignments.clear()
    }
}
