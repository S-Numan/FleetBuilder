package fleetBuilder.consoleCommands.saveTransfer

import com.fs.starfarer.api.Global
import fleetBuilder.util.MISC
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console


class CopySave : BaseCommand {

    val handleCargo = true
    val handleRelations = false
    val handleFleet = true
    val handleOfficers = true//handleFleet must be true
    val handleKnownBlueprints = true
    val handleKnownHullmods = true
    val handlePlayer = true
    val handleCredits = true

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }


        val json = MISC.createPlayerSaveJson(
            handleCargo = handleCargo,
            handleRelations = handleRelations,
            handleKnownBlueprints = handleKnownBlueprints,
            handlePlayer = handlePlayer,
            handleFleet = handleFleet,
            handleCredits = handleCredits,
            handleKnownHullmods = handleKnownHullmods,
            handleOfficers = handleOfficers
        )

        Global.getSettings().writeJSONToCommon("SaveTransfer/CopySave", json, false)
        //Global.getSector().
        Console.showMessage("Save Complete")
        return BaseCommand.CommandResult.SUCCESS
    }
}