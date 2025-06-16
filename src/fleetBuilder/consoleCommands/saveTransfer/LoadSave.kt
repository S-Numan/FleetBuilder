package fleetBuilder.consoleCommands.saveTransfer

import com.fs.starfarer.api.Global
import fleetBuilder.misc.MISC
import org.apache.log4j.Level
import org.json.JSONObject
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console


class LoadSave : BaseCommand {

    val handleCargo = true
    val handleRelations = false
    val handleFleet = true
    val handleOfficers = true//handleFleet must be true
    val handleKnownBlueprints = true
    val handleKnownHullMods = true
    val handlePlayer = true
    val handleCredits = true


    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }
        var filename = ""
        if(args.isEmpty()) {
           filename = "CopySave"
        } else {
            filename = args
        }
        val configPath = "SaveTransfer/$filename"

        if(!Global.getSettings().fileExistsInCommon(configPath)) {
            Console.showMessage("Failed to find $configPath")
            return BaseCommand.CommandResult.ERROR
        }

        val json: JSONObject
        try {
            json = Global.getSettings().readJSONFromCommon(configPath, false)
        } catch (e: Exception) {
            Console.showMessage("Failed to read json\n$e")
            return BaseCommand.CommandResult.ERROR
        }

        val missing = MISC.loadPlayerSaveJson(json,
            handleCargo = handleCargo,
            handleRelations = handleRelations,
            handleKnownBlueprints = handleKnownBlueprints,
            handlePlayer = handlePlayer,
            handleFleet = handleFleet,
            handleCredits = handleCredits,
            handleKnownHullmods = handleKnownHullMods,
            handleOfficers = handleOfficers
        )


        if (!missing.hasMissing()) {
            Console.showMessage("No missing elements.")
        } else {
            if (missing.weaponIds.isNotEmpty()) {
                Console.showMessage("Missing Weapons:\n" + missing.weaponIds.joinToString("\n"), Level.ERROR)
            }

            if (missing.wingIds.isNotEmpty()) {
                Console.showMessage("Missing Wings:\n" + missing.wingIds.joinToString("\n"), Level.ERROR)
            }

            if (missing.hullModIds.isNotEmpty()) {
                Console.showMessage("Missing Hull Mods:\n" + missing.hullModIds.joinToString("\n"), Level.ERROR)
            }

            if (missing.hullIds.isNotEmpty()) {
                Console.showMessage("Missing Hulls:\n" + missing.hullIds.joinToString("\n"), Level.ERROR)
            }

            if (missing.gameMods.isNotEmpty()) {
                val formattedMods = missing.gameMods.joinToString("\n") { (id, name, version) ->
                    "Mod ID: $id, Name: $name, Version: $version"
                }
                Console.showMessage("Missing Game Mods:\n$formattedMods", Level.ERROR)
            }
        }


        Console.showMessage("Load Complete")
        return BaseCommand.CommandResult.SUCCESS

    }
}