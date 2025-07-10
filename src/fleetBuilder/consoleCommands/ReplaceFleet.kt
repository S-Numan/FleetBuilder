package fleetBuilder.consoleCommands

import com.fs.starfarer.api.Global
import fleetBuilder.features.CommanderShuttle.addPlayerShuttle
import fleetBuilder.features.CommanderShuttle.removePlayerShuttle
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.FleetSerialization.getFleetFromJson
import fleetBuilder.util.ClipboardUtil.getClipboardTextSafe
import fleetBuilder.util.MISC.reportMissingElements
import fleetBuilder.util.MISC.updateFleetPanelContents
import org.json.JSONObject
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console
import org.lazywizard.console.commands.AddCrew
import org.lazywizard.console.commands.AddFuel
import org.lazywizard.console.commands.AddSupplies
import org.lazywizard.console.commands.Repair

class ReplaceFleet : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val clipboard = getClipboardTextSafe()
        val json: JSONObject
        try {
            json = JSONObject(clipboard)
        } catch (e: Exception) {
            Console.showMessage("Failed to convert string to json")
            return BaseCommand.CommandResult.ERROR
        }

        val playerFleet = Global.getSector().playerFleet

        for (member in playerFleet.fleetData.membersListCopy) {
            playerFleet.fleetData.removeFleetMember(member)
        }
        for (officer in playerFleet.fleetData.officersCopy) {
            playerFleet.fleetData.removeOfficer(officer.person)
        }

        addPlayerShuttle()

        val missingElements = getFleetFromJson(
            json, playerFleet,
            FleetSerialization.FleetSettings().apply {
                includeCommanderSetFlagship = false//The player is always commanding the flagship. Thus if this isn't false, the player will displace the officer of that ship with themselves.
            }
        )

        reportMissingElements(missingElements)


        //Need to move the shuttle to the last member in the fleet, but I don't care enough to do this properly.
        removePlayerShuttle()
        addPlayerShuttle()

        Console.showMessage("Fleet overwritten")

        val aggression = json.optInt("aggression_doctrine", 2)
        Global.getSector().playerFaction.doctrine.aggression = aggression

        Console.showMessage("Aggression doctrine set")

        updateFleetPanelContents()

        playerFleet.cargo.removeCrew(playerFleet.cargo.crew)
        playerFleet.cargo.removeSupplies(playerFleet.cargo.supplies)
        playerFleet.cargo.removeFuel(playerFleet.cargo.fuel)

        AddCrew().runCommand("", context)
        AddSupplies().runCommand("", context)
        AddFuel().runCommand("", context)
        Repair().runCommand("", context)

        return BaseCommand.CommandResult.SUCCESS
    }
}