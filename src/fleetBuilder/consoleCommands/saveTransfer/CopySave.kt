package fleetBuilder.consoleCommands.saveTransfer

import fleetBuilder.config.FBTxt
import fleetBuilder.util.PlayerSaveUtil
import fleetBuilder.util.lib.ClipboardUtil.setClipboardText
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console


class CopySave : BaseCommand {

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

        val argList = args.lowercase().split(" ")
        val json = PlayerSaveUtil.createPlayerSaveJson(
            handleCargo = !argList.contains(NO_CARGO),
            handleRelations = !argList.contains(NO_REP),
            handleKnownBlueprints = !argList.contains(NO_BLUEPRINTS),
            handlePlayer = !argList.contains(NO_PLAYER),
            handleFleet = !argList.contains(NO_FLEET),
            handleCredits = !argList.contains(NO_CREDITS),
            handleKnownHullmods = !argList.contains(NO_HULLMODS),
            handleOfficers = !argList.contains(NO_OFFICERS)
        )

        setClipboardText(json.toString(4))

        Console.showMessage(FBTxt.txt("save_copied"))
        return BaseCommand.CommandResult.SUCCESS
    }
}