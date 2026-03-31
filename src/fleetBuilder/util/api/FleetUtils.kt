package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ModSpecAPI
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.RepairTrackerAPI
import fleetBuilder.features.commanderShuttle.CommanderShuttle.addPlayerShuttle
import fleetBuilder.features.commanderShuttle.CommanderShuttle.playerShuttleExists
import fleetBuilder.features.commanderShuttle.CommanderShuttle.removePlayerShuttle
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.DataFleet.buildFleetFull
import fleetBuilder.serialization.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import fleetBuilder.util.api.CargoUtils.getFractionHoldableSupplies
import fleetBuilder.util.api.MemberUtils.getAllSourceModsFromMember
import fleetBuilder.util.api.PersonUtils.copyOfficerDataTo
import second_in_command.specs.SCSpecStore
import kotlin.math.max

object FleetUtils {

    fun getAllSourceModsFromFleet(
        fleet: FleetDataAPI,
        settings: FleetSettings = FleetSettings()
    ): Set<ModSpecAPI> {
        return getAllSourceModsFromFleet(getFleetDataFromFleet(fleet, settings))
    }

    fun getAllSourceModsFromFleet(data: DataFleet.ParsedFleetData): Set<ModSpecAPI> {
        val sourceMods = mutableSetOf<ModSpecAPI>()

        data.members.forEach {
            sourceMods.addAll(getAllSourceModsFromMember(it))
        }
        data.idleOfficers.forEach {
            sourceMods.addAll(PersonUtils.getAllSourceModsFromPerson(it))
        }

        if (data.secondInCommandData != null && Global.getSettings().modManager.isModEnabled("second_in_command")) {
            data.secondInCommandData.officers.forEach {
                sourceMods.addAll(PersonUtils.getAllSourceModsFromPerson(it.person))
                val aptitudeMod = SCSpecStore.getAptitudeSpec(it.aptitudeId)?.modSpec
                if (aptitudeMod != null)
                    sourceMods.add(aptitudeMod)
            }
        }

        return sourceMods
    }

    fun getUnassignedOfficers(fleet: FleetDataAPI): List<PersonAPI> {
        return fleet.officersCopy.map { it.person }.filter { fleet.getMemberWithCaptain(it) == null }
    }

    fun getAssignedOfficers(fleet: FleetDataAPI): List<PersonAPI> {
        return fleet.officersCopy.map { it.person }.filter { fleet.getMemberWithCaptain(it) != null }
    }

    fun fullFleetRepair(fleet: FleetDataAPI) {
        fleet.membersListCopy.forEach { member ->
            member.status.repairFully()

            val repairs: RepairTrackerAPI = member.repairTracker
            repairs.cr = max(repairs.cr, repairs.maxCR)
            member.setStatUpdateNeeded(true)
        }
    }

    fun replacePlayerFleetWith(
        fleet: CampaignFleetAPI,
        aggression: Int = -1,
        replacePlayer: Boolean = false,
        settings: FleetSettings = FleetSettings()
    ) {
        val playerFleet = Global.getSector()?.playerFleet ?: return

        // Clear current fleet members and officers
        for (member in playerFleet.fleetData.membersListCopy)
            playerFleet.fleetData.removeFleetMember(member)
        for (officer in playerFleet.fleetData.officersCopy)
            playerFleet.fleetData.removeOfficer(officer.person)

        addPlayerShuttle()

        //Copy the fleet over
        val dataFleet = getFleetDataFromFleet(fleet, settings)

        val playerPerson = Global.getSector().playerPerson
        var newCommander: PersonAPI? = null

        if (replacePlayer) {
            val newCommanderData = dataFleet.commanderIfNoFlagship
                ?: dataFleet.members.find { it.isFlagship }?.personData
            if (newCommanderData != null) {
                newCommander = DataPerson.buildPersonFull(newCommanderData, settings = settings.memberSettings.personSettings)
                copyOfficerDataTo(newCommander, playerPerson)
                Global.getSector().characterData.setName(newCommander.name.fullName, newCommander.gender)
            }
        }

        settings.includeCommanderSetFlagship = false//The player is always commanding the flagship. Thus if this isn't false, the player will displace the officer of that ship with themselves.
        settings.includeAggression = false//We do this manually for the player faction

        buildFleetFull(dataFleet, playerFleet.fleetData, settings)

        if (newCommander != null) {
            // Look for a fleet member commanded by an officer matching the new commander
            val matchingMember = playerFleet.membersWithFightersCopy.find { member ->
                val officer = member.captain
                officer != null && !officer.isPlayer && officer.nameString == newCommander.nameString &&
                        officer.stats.level == newCommander.stats.level &&
                        officer.stats.skillsCopy.map { it.skill.id to it.level }.toSet() ==
                        newCommander.stats.skillsCopy.map { it.skill.id to it.level }.toSet()
            }

            // If found, remove the officer and assign the player as captain
            if (matchingMember != null) {
                removePlayerShuttle()

                playerFleet.fleetData.removeOfficer(matchingMember.captain)
                matchingMember.captain = playerPerson
                //Console.showMessage("Replaced duplicate officer with player captain: ${playerPerson.nameString}")
            }
        }

        Global.getSector().playerPerson?.memoryWithoutUpdate?.set("\$FB_NO-OVER-OFFICER-LIMIT-MOTHBALL", true)

        if (playerShuttleExists()) {
            //Need to move the shuttle to the last member in the fleet, but I don't care enough to do this properly.
            removePlayerShuttle()
            addPlayerShuttle()
        }

        if (aggression != -1) {
            Global.getSector().playerFaction.doctrine.aggression = aggression
        }
    }

    fun fulfillPlayerFleet() {
        val playerFleet = Global.getSector()?.playerFleet ?: return

        // Crew
        val neededCrew = playerFleet.cargo.maxPersonnel - playerFleet.cargo.crew
        playerFleet.cargo.addCrew(neededCrew.toInt())

        // Supplies
        val maxCargoFraction = 0.5f

        val maxSupplies = playerFleet.cargo.maxCapacity - playerFleet.cargo.supplies
        val suppliesToAdd = getFractionHoldableSupplies(playerFleet.cargo, maxCargoFraction).coerceAtMost(maxSupplies.toInt())
        playerFleet.cargo.addSupplies(suppliesToAdd.toFloat())

        // Fuel
        val fuelToAdd = playerFleet.cargo.freeFuelSpace
        playerFleet.cargo.addFuel(fuelToAdd.toFloat())

        // Remove excess supplies
        if (playerFleet.cargo.supplies > playerFleet.cargo.maxCapacity * maxCargoFraction) {
            val overflow = playerFleet.cargo.supplies - playerFleet.cargo.maxCapacity * maxCargoFraction

            playerFleet.cargo.removeSupplies(overflow)
        }

        // Remove excess fuel
        if (playerFleet.cargo.fuel > playerFleet.cargo.maxFuel) {
            playerFleet.cargo.removeFuel(playerFleet.cargo.fuel - playerFleet.cargo.maxFuel)
        }

        // Remove excess crew
        if (playerFleet.cargo.totalPersonnel > playerFleet.cargo.maxPersonnel) {
            val removable = playerFleet.cargo.crew - playerFleet.fleetData.minCrew
            val overflow = playerFleet.cargo.totalPersonnel - playerFleet.cargo.maxPersonnel

            if (removable > 0) {
                playerFleet.cargo.removeCrew(minOf(removable, overflow).toInt())
            }
        }
        // Repair
        fullFleetRepair(playerFleet.fleetData)

        updateFleetPanelContents()
    }
}