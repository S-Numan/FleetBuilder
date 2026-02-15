package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import fleetBuilder.util.FBTxt
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.toBoolean
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommandWithSuggestion
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
        val skillId = Global.getSettings().skillIds.firstOrNull { it == skillIdInput }

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

        Console.showMessage(FBTxt.txt("done"))

        return BaseCommand.CommandResult.SUCCESS
    }

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        if (parameter != 0) return ArrayList<String?>()

        return Global.getSettings().skillIds
    }
}