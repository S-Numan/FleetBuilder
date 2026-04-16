package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.completelyRemoveMod
import fleetBuilder.util.api.kotlin.safeInvoke
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.BaseCommandWithSuggestion
import org.lazywizard.console.CommandUtils.findBestStringMatch
import org.lazywizard.console.Console

class AddHullMod : BaseCommandWithSuggestion {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        val refitPanel = ReflectionMisc.getRefitPanel()
        if (refitPanel == null && !context.isInCampaign) {
            Console.showMessage("Error: This command can only be used in the campaign or refit tab.")
            return CommandResult.WRONG_CONTEXT
        }
        if (args.isEmpty()) {
            return CommandResult.BAD_SYNTAX
        }

        val argList = args.split(" ")

        val modIdInput = argList.getOrNull(0)
        val modId = findBestStringMatch(modIdInput, LookupUtils.getHullModIDSet())

        if (modId == null) {
            Console.showMessage("No modspec found with id '$modIdInput'! Use 'list hullmods' for a complete list of valid ids.")
            return BaseCommand.CommandResult.ERROR
        }

        if (refitPanel == null) {
            val cargo = Global.getSector()?.playerFleet?.cargo ?: return BaseCommand.CommandResult.ERROR

            cargo.addHullmods(modId, 1)
            Console.showMessage("Added modspec $modId to player inventory.")
            return CommandResult.SUCCESS
        } else {
            val isOf = argList.getOrNull(1)?.lowercase()

            val variant = ReflectionMisc.getCurrentVariantInRefitTab()
            if (variant == null) {
                Console.showMessage("Failed to get variant in refit screen")
                return BaseCommand.CommandResult.ERROR
            }

            variant.completelyRemoveMod(modId)

            val addType: String = when {
                isOf == null -> ""
                "smod".startsWith(isOf) -> {
                    variant.addPermaMod(modId, true)
                    "S-Mod "
                }
                "perma".startsWith(isOf) -> {
                    variant.addPermaMod(modId, false)
                    "Perma "
                }
                else -> {
                    variant.addMod(modId)
                    ""
                }
            }

            refitPanel.safeInvoke("syncWithCurrentVariant")


            Console.showMessage("Added ${addType}hull-mod of id '$modId' to currently viewed variant of hull '${variant.hullSpec.hullName}'")

            return BaseCommand.CommandResult.SUCCESS
        }
    }

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        return when (parameter) {
            0 -> LookupUtils.getHullModIDSet().toMutableList()//.filterNot { LookupUtils.getHullModSpec(it)?.isHidden == true }.toMutableList()
            //1 -> mutableListOf("true", "false")
            //2 -> mutableListOf("true", "false")
            else -> ArrayList()
        }
    }
}