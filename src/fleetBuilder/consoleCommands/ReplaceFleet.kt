package fleetBuilder.consoleCommands

import fleetBuilder.util.ClipboardUtil.getClipboardTextSafe
import fleetBuilder.util.FBMisc.fulfillPlayerFleet
import fleetBuilder.util.FBMisc.replacePlayerFleetWith
import fleetBuilder.util.FBMisc.reportMissingElementsIfAny
import org.json.JSONObject
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console


class ReplaceFleet : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val clipboard = getClipboardTextSafe()
        val json: JSONObject
        try {
            json = JSONObject(clipboard)
        } catch (e: Exception) {
            Console.showMessage("Failed to convert string to json")
            return BaseCommand.CommandResult.ERROR
        }

        val missing = replacePlayerFleetWith(json)
        reportMissingElementsIfAny(missing)

        fulfillPlayerFleet()

        return BaseCommand.CommandResult.SUCCESS
    }
}