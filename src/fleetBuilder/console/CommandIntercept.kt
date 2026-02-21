package fleetBuilder.console

import fleetBuilder.ui.customPanel.DialogUtil
import fleetBuilder.util.ReflectionMisc
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommandListener
import org.lazywizard.console.Console

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
        if (command.lowercase() == "forcedismissdialog") {
            if (DialogUtil.forceCloseAllDialogs()) {
                Console.showMessage("Forcibly closed CustomUIPanel dialog")
            }
        }

        if (result != BaseCommand.CommandResult.SUCCESS) return

        if (command.lowercase() == "addship") {
            ReflectionMisc.updateFleetPanelContents()
        }
    }
}