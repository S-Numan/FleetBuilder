package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.core.FBTxt
import fleetBuilder.util.*
import fleetBuilder.util.kotlin.completelyRemoveMod
import fleetBuilder.util.kotlin.safeInvoke
import fleetBuilder.util.kotlin.toBoolean
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommandWithSuggestion
import org.lazywizard.console.Console

class AddHullMod : BaseCommandWithSuggestion {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        val refitPanel = ReflectionMisc.getRefitPanel()
        if (refitPanel == null) {
            Console.showMessage("Must be in refit tab")
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val argList = args.lowercase().split(" ")

        val modIdInput = argList.getOrNull(0)
        val modId = Global.getSettings().allHullModSpecs.firstOrNull { it.id == modIdInput }?.id

        if (modId == null) {
            Console.showMessage("Could not find hullmod_id with id '$modIdInput'")
            return BaseCommand.CommandResult.ERROR
        }

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

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        if (parameter != 0) return ArrayList<String?>()

        return LookupUtils.getHullModIDSet().toMutableList()
    }
}