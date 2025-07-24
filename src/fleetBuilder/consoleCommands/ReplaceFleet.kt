package fleetBuilder.consoleCommands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.RepairTrackerAPI
import fleetBuilder.features.CommanderShuttle.addPlayerShuttle
import fleetBuilder.features.CommanderShuttle.removePlayerShuttle
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.FleetSerialization.getFleetFromJson
import fleetBuilder.util.ClipboardUtil.getClipboardTextSafe
import fleetBuilder.util.FBMisc.fullFleetRepair
import fleetBuilder.util.FBMisc.getFractionHoldableSupplies
import fleetBuilder.util.FBMisc.getMaxHoldableCrew
import fleetBuilder.util.FBMisc.replacePlayerFleetWith
import fleetBuilder.util.FBMisc.reportMissingElementsIfAny
import fleetBuilder.util.ReflectionMisc.updateFleetPanelContents
import org.json.JSONObject
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console
import kotlin.math.max


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

        return BaseCommand.CommandResult.SUCCESS
    }
}