package fleetBuilder.consoleCommands

import fleetBuilder.util.Reporter
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console

class ReloadShipDirectories : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {

        Reporter.onApplicationLoad()

        Console.showMessage("Reloaded ship directories")

        return BaseCommand.CommandResult.SUCCESS
    }
}