package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.toBoolean
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommandWithSuggestion
import org.lazywizard.console.CommandUtils.findBestStringMatch
import org.lazywizard.console.Console


class AddSkill : BaseCommandWithSuggestion {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        val refitPanel = ReflectionMisc.getRefitPanel()
        if (refitPanel == null) {
            Console.showMessage("Must be in refit tab")
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val argList = args.lowercase().split(" ")

        val skillIdInput = argList.getOrNull(0)
        val skillId = findBestStringMatch(skillIdInput, Global.getSettings().skillIds)

        if (skillId == null) {
            Console.showMessage("Could not find skill_id with id '$skillIdInput'")
            return BaseCommand.CommandResult.ERROR
        }

        val eliteArg = argList.getOrNull(1)
        val isElite = eliteArg != null && (eliteArg.toBoolean == true || "elite".startsWith(eliteArg.lowercase()))

        val member = ReflectionMisc.getCurrentMemberInRefitTab()
        if (member == null) {
            Console.showMessage("Failed to get member in refit screen")
            return BaseCommand.CommandResult.ERROR
        }
        if (member.captain == null || member.captain.isDefault) {
            Console.showMessage("Ship has no officer to add skill to")
            return BaseCommand.CommandResult.ERROR
        }
        if (member.captain.stats.getSkillLevel(skillId) > 0) {
            Console.showMessage("Skill already added")
            return BaseCommand.CommandResult.ERROR
        }

        member.captain.stats.setSkillLevel(skillId, if (!isElite) 1f else 2f)

        Console.showMessage("Added ${if (isElite) "elite " else " "}skill of ID '$skillId' to officer '${member.captain.name.fullName}'")

        return BaseCommand.CommandResult.SUCCESS
    }

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        return when (parameter) {
            0 -> Global.getSettings().skillIds
            1 -> mutableListOf("elite")
            else -> ArrayList()
        }
    }
}