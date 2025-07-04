package fleetBuilder.consoleCommands.saveTransfer

import com.fs.starfarer.api.Global
import fleetBuilder.util.ClipboardUtil.getClipboardJson
import fleetBuilder.util.MISC
import org.apache.log4j.Level
import org.json.JSONObject
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console


class LoadSave : BaseCommand {

    private val NO_REP = "-no-rep"
    private val NO_FLEET = "-no-fleet"
    private val NO_CARGO = "-no-cargo"
    private val NO_HULLMODS = "-no-hullmods"
    private val NO_BLUEPRINTS = "-no-blueprints"
    private val NO_OFFICERS = "-no-officers"
    private val NO_PLAYER = "-no-player"
    private val NO_CREDITS = "-no-credits"

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val json: JSONObject?

        val argList = args.lowercase().split(" ")
        
        if (argList.contains("-backup")) {
            val configPath = "SaveTransfer/lastSave"

            if (!Global.getSettings().fileExistsInCommon(configPath)) {
                Console.showMessage("Failed to find backup save")
                return BaseCommand.CommandResult.ERROR
            }
            try {
                json = Global.getSettings().readJSONFromCommon(configPath, false)
            } catch (e: Exception) {
                Console.showMessage("Failed to read backup save json\n$e")
                return BaseCommand.CommandResult.ERROR
            }
        } else {
            json = getClipboardJson()
        }

        if (json == null) {
            Console.showMessage("Failed to read json in clipboard\n")
            return BaseCommand.CommandResult.ERROR
        }

        val missing = MISC.loadPlayerSaveJson(
            json,
            handleCargo = !argList.contains(NO_CARGO),
            handleRelations = !argList.contains(NO_REP),
            handleKnownBlueprints = !argList.contains(NO_BLUEPRINTS),
            handlePlayer = !argList.contains(NO_PLAYER),
            handleFleet = !argList.contains(NO_FLEET),
            handleCredits = !argList.contains(NO_CREDITS),
            handleKnownHullmods = !argList.contains(NO_HULLMODS),
            handleOfficers = !argList.contains(NO_OFFICERS)
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

            if (missing.skillIds.isNotEmpty()) {
                Console.showMessage("Missing Skills:\n" + missing.skillIds.joinToString("\n"), Level.ERROR)
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