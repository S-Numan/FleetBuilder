package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.util.ReflectionMisc
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommandWithSuggestion
import org.lazywizard.console.CommandUtils.findBestStringMatch
import org.lazywizard.console.Console


class RemoveSkill : BaseCommandWithSuggestion {
    override fun runCommand(args: String, context: CommandContext): BaseCommand.CommandResult {
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

        val member = ReflectionMisc.getCurrentMemberInRefitTab()
        if (member == null) {
            Console.showMessage("Failed to get member in refit screen")
            return BaseCommand.CommandResult.ERROR
        }
        if (member.captain == null || member.captain.isDefault) {
            Console.showMessage("Ship has no officer to remove a skill from")
            return BaseCommand.CommandResult.ERROR
        }
        if (!member.captain.stats.hasSkill(skillId) || member.captain.stats.getSkillLevel(skillId) == 0f) {
            Console.showMessage("Officer does not have skill")
            return BaseCommand.CommandResult.ERROR
        }

        member.captain.stats.setSkillLevel(skillId, 0f)

        Console.showMessage("Removed skill of ID '$skillId' from officer '${member.captain.name.fullName}'")

        return BaseCommand.CommandResult.SUCCESS
    }

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        if (parameter != 0)
            return ArrayList()

        val member = ReflectionMisc.getCurrentMemberInRefitTab()
        if (member != null && member.captain != null && !member.captain.isDefault) {
            return member.captain.stats.skillsCopy.filter { it.level > 0f }.map { it.skill.id }.toMutableList()
        }

        return ArrayList()
    }
}