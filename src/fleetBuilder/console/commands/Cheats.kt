package fleetBuilder.console.commands

import fleetBuilder.core.FBSettings
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.Console

class Cheats : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (FBSettings.cheatsEnabledInConsole()) {
            FBSettings.setCheatsEnabledInConsole(false)
            if (FBSettings.cheatsEnabledInSettings())
                Console.showMessage("Cheats disabled in console, however, cheats are still enabled in the LunaLib settings.")
            else
                Console.showMessage("Cheats disabled")
        } else {
            FBSettings.setCheatsEnabledInConsole(false)
            if (FBSettings.cheatsEnabledInSettings())
                Console.showMessage("Cheats enabled in console, however, cheats are already enabled in the LunaLib settings.")
            else
                Console.showMessage("Cheats enabled")
        }

        return BaseCommand.CommandResult.SUCCESS
    }
}