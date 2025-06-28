package fleetBuilder.consoleCommands

import fleetBuilder.variants.LoadoutManager
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console

class ReloadShipDirectories : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {

        LoadoutManager.loadAllDirectories()

        Console.showMessage("Reloaded ship directories")

        return BaseCommand.CommandResult.SUCCESS
    }
}