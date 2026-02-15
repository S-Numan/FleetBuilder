package fleetBuilder.console

import fleetBuilder.util.ReflectionMisc
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommandListener

class CommandIntercept : CommandListener {
    override fun onPreExecute(
        command: String,
        args: String,
        context: BaseCommand.CommandContext,
        alreadyIntercepted: Boolean
    ): Boolean {
        return false // do nothing
    }

    override fun execute(
        command: String,
        args: String,
        context: BaseCommand.CommandContext
    ): BaseCommand.CommandResult {
        return BaseCommand.CommandResult.SUCCESS // do nothing
    }

    override fun onPostExecute(
        command: String,
        args: String,
        result: BaseCommand.CommandResult,
        context: BaseCommand.CommandContext,
        interceptedBy: CommandListener?
    ) {
        if (result != BaseCommand.CommandResult.SUCCESS) return

        if (command.lowercase() == "addship") {
            ReflectionMisc.updateFleetPanelContents()
        }
    }
}