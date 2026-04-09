package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.core.FBTxt
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.completelyRemoveMod
import fleetBuilder.util.api.kotlin.safeInvoke
import fleetBuilder.util.api.kotlin.toBoolean
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.BaseCommandWithSuggestion
import org.lazywizard.console.Console

class AddHullMod : BaseCommandWithSuggestion {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        val refitPanel = ReflectionMisc.getRefitPanel()
        if (refitPanel == null && !context.isInCampaign) {
            Console.showMessage("Error: This command must be used in the refit tab or the campaign.")
            return CommandResult.WRONG_CONTEXT
        }
        if (args.isEmpty()) {
            return CommandResult.BAD_SYNTAX
        }

        val argList = args.lowercase().split(" ")

        val modIdInput = argList.getOrNull(0)
        val modId = modIdInput?.let { LookupUtils.getHullModSpec(modIdInput) }?.id

        if (modId == null) {
            Console.showMessage("No modspec found with id '$modId'! Use 'list hullmods' for a complete list of valid ids.")
            return BaseCommand.CommandResult.ERROR
        }

        if (refitPanel == null) {
            val cargo = Global.getSector()?.playerFleet?.cargo ?: return BaseCommand.CommandResult.ERROR

            cargo.addHullmods(modId, 1)
            Console.showMessage("Added modspec $modId to player inventory.")
            return CommandResult.SUCCESS
        } else {
            val isPerma = argList.getOrNull(1)?.toBoolean ?: false
            val isSMod = argList.getOrNull(2)?.toBoolean ?: false

            val variant = ReflectionMisc.getCurrentVariantInRefitTab()
            if (variant == null) {
                Console.showMessage("Failed to get variant in refit screen")
                return BaseCommand.CommandResult.ERROR
            }

            variant.completelyRemoveMod(modId)

            if (!isPerma && !isSMod) {
                variant.addMod(modId)
            } else {
                variant.addPermaMod(modId, isSMod)
            }
            refitPanel.safeInvoke("syncWithCurrentVariant")

            Console.showMessage(FBTxt.txt("done"))

            return BaseCommand.CommandResult.SUCCESS
        }
    }

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        if (parameter != 0) return ArrayList<String?>()

        return LookupUtils.getHullModIDSet().toMutableList()
    }
}