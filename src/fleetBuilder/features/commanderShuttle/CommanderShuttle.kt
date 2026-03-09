package fleetBuilder.features.commanderShuttle

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Tags
import fleetBuilder.core.ModSettings
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.listeners.OfficerChangeEvents

class CommanderShuttle : CurrentLocationChangedListener {
    override fun reportCurrentLocationChanged(prev: LocationAPI?, curr: LocationAPI?) {
        if (prev == null || curr == null)
            return

        commanderShuttleListener.reportCurrentLocationChanged(prev, curr)
    }

    companion object {
        var commanderShuttleListener = CommanderShuttleListener()//Should not be present as a script if the shuttle is not in the player's fleet

        fun onGameLoad(newGame: Boolean) {
            val sector = Global.getSector()

            if (Global.getSector().memoryWithoutUpdate.getBoolean("\$FB_hadCommandShuttle")) {
                addPlayerShuttle()

                sector.addTransientScript(commanderShuttleListener)
                sector.addTransientListener(commanderShuttleListener)
            }

            OfficerChangeEvents.addTransientListener { change ->
                //Remove commandShuttle if was piloted by player and is no longer
                if (change.previous != null && change.previous.isPlayer &&
                    change.member.variant?.hasHullMod(ModSettings.commandShuttleId) == true
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
            val sector = Global.getSector()

            var hadShuttle = false
            for (member in Global.getSector().playerFleet.fleetData.membersListCopy) {
                if (member.variant.hasHullMod(ModSettings.commandShuttleId)) {
                    member.variant.removeMod(ModSettings.commandShuttleId)
                    Global.getSector().playerFleet.fleetData.removeFleetMember(member)
                    hadShuttle = true
                }
            }
            if (hadShuttle) {
                sector.removeTransientScript(commanderShuttleListener)
                sector.removeListener(commanderShuttleListener)
            }

            ReflectionMisc.updateFleetPanelContents()
        }

        fun addPlayerShuttle() {
            if (playerShuttleExists()) return

            val sector = Global.getSector()

            sector.addTransientScript(commanderShuttleListener)
            sector.addTransientListener(commanderShuttleListener)

            val shuttleMember = Global.getSettings().createFleetMember(FleetMemberType.SHIP, "shuttlepod_Hull")

            shuttleMember.shipName = "Command Shuttle"
            shuttleMember.variant.addMod(ModSettings.commandShuttleId)
            shuttleMember.variant.addTag(Tags.NO_SELL)
            shuttleMember.variant.addTag(Tags.RESTRICTED)
            shuttleMember.variant.addTag(Tags.NO_SIM)
            shuttleMember.variant.addTag(Tags.NO_BATTLE_SALVAGE)
            shuttleMember.variant.addTag(Tags.HULLMOD_NO_DROP_SALVAGE)
            shuttleMember.variant.addTag(Tags.UNRECOVERABLE)
            shuttleMember.variant.addTag(Tags.HIDE_IN_CODEX)
            shuttleMember.variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE)

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
                if (member.variant.hasHullMod(ModSettings.commandShuttleId)) {
                    return true
                }
            }
            return false
        }

    }


}