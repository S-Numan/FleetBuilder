package fleetBuilder.consoleCommands

import com.fs.starfarer.api.Global
import fleetBuilder.util.getUnassignedOfficers
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console

class RemoveIdleOfficers : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }
        
        val fleet = Global.getSector()?.playerFleet?.fleetData ?: return BaseCommand.CommandResult.ERROR

        val idleOfficers = fleet.getUnassignedOfficers()

        var count = 0
        idleOfficers.forEach {
            fleet.removeOfficer(it)
            count++
        }

        Console.showMessage("Removed $count idle officers")

        return BaseCommand.CommandResult.SUCCESS
    }

}