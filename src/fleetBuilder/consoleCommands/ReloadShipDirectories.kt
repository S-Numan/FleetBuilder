package fleetBuilder.consoleCommands

import fleetBuilder.config.FBTxt
import fleetBuilder.variants.LoadoutManager
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console

class ReloadShipDirectories : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {

        LoadoutManager.loadAllDirectories()

        Console.showMessage(FBTxt.txt("done"))

        return BaseCommand.CommandResult.SUCCESS
    }
}