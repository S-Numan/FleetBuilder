package fleetBuilder.console.commands

import com.fs.starfarer.api.impl.campaign.ids.Personalities
import fleetBuilder.util.ReflectionMisc
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.BaseCommandWithSuggestion
import org.lazywizard.console.CommandUtils.findBestStringMatch
import org.lazywizard.console.Console

class SetOfficerPersonality : BaseCommandWithSuggestion {
    val personalities = listOf(Personalities.TIMID, Personalities.CAUTIOUS, Personalities.STEADY, Personalities.AGGRESSIVE, Personalities.RECKLESS)
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        val refitPanel = ReflectionMisc.getRefitPanel()
        if (refitPanel == null) {
            Console.showMessage("Must be in refit tab")
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }
        if (args.isEmpty()) {
            return CommandResult.BAD_SYNTAX
        }

        val member = ReflectionMisc.getCurrentMemberInRefitTab()
        if (member == null) {
            Console.showMessage("Failed to get member in refit screen")
            return BaseCommand.CommandResult.ERROR
        }
        if (member.captain == null || member.captain.isDefault) {
            Console.showMessage("Ship has no officer to change personality of")
            return BaseCommand.CommandResult.ERROR
        }

        val argList = args.lowercase().split(" ")
        val inputPersonality = argList.getOrNull(0)

        val personality = findBestStringMatch(inputPersonality, personalities)
        if (personality == null) {
            Console.showMessage("Invalid personality: $inputPersonality")
            return CommandResult.BAD_SYNTAX
        }
        if (member.captain.personalityAPI.id.lowercase() != personality) {
            member.captain.setPersonality(personality)
            Console.showMessage("Set personality of officer to $personality")
        } else {
            Console.showMessage("Officer personality is already set to $personality. No changes were made")
        }

        return BaseCommand.CommandResult.SUCCESS
    }

    override fun getSuggestions(
        parameter: Int,
        previous: MutableList<String?>?,
        context: CommandContext?
    ): MutableList<String?> {
        if (parameter != 0) return ArrayList()

        return personalities.toMutableList()
    }
}