package fleetBuilder.console.commands

import fleetBuilder.core.shipDirectory.ShipDirectoryService
import fleetBuilder.util.FBTxt
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console

class ReloadShipDirectories : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {

        ShipDirectoryService.loadAllDirectories()

        Console.showMessage(FBTxt.txt("done"))

        return BaseCommand.CommandResult.SUCCESS
    }
}