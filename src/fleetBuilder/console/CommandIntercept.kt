package fleetBuilder.console

import fleetBuilder.console.commands.AddHullMod
import fleetBuilder.console.commands.AddXP
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.CampaignUtils
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
        if (!alreadyIntercepted) {
            if (command.lowercase() == "addhullmod" || command.lowercase() == "addxp")
                return true // intercept
        }

        return false // do nothing
    }

    override fun execute(
        command: String,
        args: String,
        context: BaseCommand.CommandContext
    ): BaseCommand.CommandResult {
        if (command.lowercase() == "addhullmod")
            return AddHullMod().runCommand(args, context)
        else if (command.lowercase() == "addxp")
            return AddXP().runCommand(args, context)

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
            if (DialogUtils.forceCloseAllDialogs())
                Console.showMessage("Forcibly closed CustomUIPanel dialog")

            if (CampaignUtils.closeCampaignDummyDialog())
                Console.showMessage("Forcibly closed Campaign Dummy Dialog")
        }

        if (result != BaseCommand.CommandResult.SUCCESS) return

        if (command.lowercase() == "addship") {
            ReflectionMisc.updateFleetPanelContents()
        }
    }
}