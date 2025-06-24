package fleetBuilder.consoleCommands

import fleetBuilder.util.MISC
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.CommandListener


class CommandIntercept : CommandListener {
    override fun onPreExecute(
        command: String,
        args: String,
        context: CommandContext,
        alreadyIntercepted: Boolean
    ): Boolean {
        return false // do nothing
    }

    override fun execute(command: String, args: String, context: CommandContext): CommandResult {
        return CommandResult.SUCCESS // do nothing
    }

    override fun onPostExecute(
        command: String,
        args: String,
        result: CommandResult,
        context: CommandContext,
        interceptedBy: CommandListener?
    ) {
        if (result != CommandResult.SUCCESS) return

        if (command.lowercase() == "addship") {
            MISC.updateFleetPanelContents()
        }
    }
}
