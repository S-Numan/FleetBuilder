package fleetBuilder.features.commanderShuttle

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Tags
import fleetBuilder.core.FBConst
import fleetBuilder.core.FBTxt
import fleetBuilder.core.listener.EventDispatcher
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.listeners.OfficerChangeEvents

internal object CommanderShuttle {
    var commanderShuttleListener = CommanderShuttleListener()//Should not be present as a script if the shuttle is not in the player's fleet

    fun onGameLoad(newGame: Boolean) {
        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$FB_hadCommandShuttle")) {
            addPlayerShuttle()

            EventDispatcher.manageTransientScript(CommanderShuttleListener::class.java) { commanderShuttleListener }
            EventDispatcher.manageTransientListener(CommanderShuttleListener::class.java) { commanderShuttleListener }
            EventDispatcher.manageTransientCampaignListener(CommanderShuttleListener::class.java) { commanderShuttleListener }
        }

        OfficerChangeEvents.addListener { change ->
            //Remove commandShuttle if was piloted by player and is no longer
            if (change.previous != null && change.previous.isPlayer &&
                change.member.variant?.hasHullMod(FBConst.COMMAND_SHUTTLE_ID) == true
            ) {
                val playerFleet = Global.getSector().playerFleet.fleetData
                playerFleet?.removeFleetMember(change.member)
                ReflectionMisc.updateFleetPanelContents()
            }
        }

        //ShipOfficerChangeEvents.addTransientListener { change ->
        //    MISC.showMessage("Officer changed on ${change.member.shipName}: Previously captained by: ${change.previous?.name?.fullName} Currently: ${change.current?.name?.fullName}")
        //}
    }


    fun beforeGameSave() {
        if (playerShuttleExists()) {
            removePlayerShuttle()
            Global.getSector().memoryWithoutUpdate["\$FB_hadCommandShuttle"] = true
        } else {
            Global.getSector().memoryWithoutUpdate["\$FB_hadCommandShuttle"] = false
        }
    }

    fun afterGameSave() {
        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$FB_hadCommandShuttle")) {
            addPlayerShuttle()
        }
    }

    fun removePlayerShuttle() {
        var hadShuttle = false
        for (member in Global.getSector().playerFleet.fleetData.membersListCopy) {
            if (member.variant.hasHullMod(FBConst.COMMAND_SHUTTLE_ID)) {
                member.variant.removeMod(FBConst.COMMAND_SHUTTLE_ID)
                Global.getSector().playerFleet.fleetData.removeFleetMember(member)
                hadShuttle = true
            }
        }
        if (hadShuttle) {
            EventDispatcher.manageTransientScript(CommanderShuttleListener::class.java, false)
            EventDispatcher.manageTransientListener(CommanderShuttleListener::class.java, false)
            EventDispatcher.manageTransientCampaignListener(CommanderShuttleListener::class.java, false)
        }

        ReflectionMisc.updateFleetPanelContents()
    }

    fun addPlayerShuttle() {
        if (playerShuttleExists()) return

        val sector = Global.getSector()

        EventDispatcher.manageTransientScript(CommanderShuttleListener::class.java) { commanderShuttleListener }
        EventDispatcher.manageTransientListener(CommanderShuttleListener::class.java) { commanderShuttleListener }
        EventDispatcher.manageTransientCampaignListener(CommanderShuttleListener::class.java) { commanderShuttleListener }

        val shuttleMember = Global.getSettings().createFleetMember(FleetMemberType.SHIP, "shuttlepod_Hull")

        shuttleMember.shipName = FBTxt.txt("command_shuttle")
        shuttleMember.variant.addPermaMod(FBConst.COMMAND_SHUTTLE_ID, false)
        shuttleMember.variant.addTag(Tags.NO_SELL)
        shuttleMember.variant.addTag(Tags.RESTRICTED)
        shuttleMember.variant.addTag(Tags.NO_SIM)
        shuttleMember.variant.addTag(Tags.NO_BATTLE_SALVAGE)
        shuttleMember.variant.addTag(Tags.HULLMOD_NO_DROP_SALVAGE)
        shuttleMember.variant.addTag(Tags.UNRECOVERABLE)
        shuttleMember.variant.addTag(Tags.HIDE_IN_CODEX)
        shuttleMember.variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE)
        shuttleMember.variant.addTag(FBConst.NO_COPY_TAG)

        sector.playerFleet.fleetData.addFleetMember(shuttleMember)
        sector.playerFleet.fleetData.setFlagship(shuttleMember)
        shuttleMember.repairTracker.cr = shuttleMember.repairTracker.maxCR

        ReflectionMisc.updateFleetPanelContents()
    }

    fun togglePlayerShuttle() {
        if (playerShuttleExists()) {
            removePlayerShuttle()
        } else {
            addPlayerShuttle()
        }
    }

    fun playerShuttleExists(): Boolean {
        for (member in Global.getSector().playerFleet.fleetData.membersListCopy) {
            if (member.variant.hasHullMod(FBConst.COMMAND_SHUTTLE_ID)) {
                return true
            }
        }
        return false
    }
}