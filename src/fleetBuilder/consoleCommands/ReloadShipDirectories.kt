package fleetBuilder.consoleCommands

import fleetBuilder.Reporter
import fleetBuilder.variants.LoadoutManager.loadAllDirectories
import fleetBuilder.variants.VariantLib
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console

class ReloadShipDirectories : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {

        Reporter.onApplicationLoad()

        Console.showMessage("Reloaded ship directories")

        return BaseCommand.CommandResult.SUCCESS
    }
}