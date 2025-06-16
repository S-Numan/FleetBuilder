package fleetBuilder.consoleCommands

import fleetBuilder.serialization.FleetSerialization.getFleetFromJson
import com.fs.starfarer.api.Global
import fleetBuilder.misc.Clipboard.getClipboardTextSafe
import fleetBuilder.misc.MISC.addPlayerShuttle
import fleetBuilder.misc.MISC.getFleetFromJsonComplainIfMissing
import fleetBuilder.misc.MISC.removePlayerShuttle
import fleetBuilder.misc.MISC.togglePlayerShuttle
import fleetBuilder.misc.MISC.showError
import fleetBuilder.misc.MISC.updateFleetPanelContents
import org.json.JSONObject
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console

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
        for(officer in playerFleet.fleetData.officersCopy) {
            playerFleet.fleetData.removeOfficer(officer.person)
        }

        addPlayerShuttle()

        getFleetFromJsonComplainIfMissing(json, playerFleet, includeCommander = false,
            setFlagship = false)//The player is always commanding the flagship. Thus if this isn't false, the player will displace the officer of that ship with themselves.

        //Need to move the shuttle to the last member in the fleet, but I don't care enough to do this properly.
        removePlayerShuttle()
        addPlayerShuttle()

        Console.showMessage("Fleet overwritten")

        val aggression = json.optInt("aggression_doctrine", 2)
        Global.getSector().playerFaction.doctrine.aggression = aggression

        Console.showMessage("Aggression doctrine set")

        updateFleetPanelContents()

        return BaseCommand.CommandResult.SUCCESS
    }
}