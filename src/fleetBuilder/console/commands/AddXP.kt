package fleetBuilder.console.commands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.getMaxOfficerLevel
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.CommandUtils
import org.lazywizard.console.Console
import kotlin.math.max
import kotlin.math.min

class AddXP : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        var officer: PersonAPI? = null

        val refitPanel = ReflectionMisc.getRefitPanel()
        if (refitPanel == null) {
            if (context.isInCampaign)
                officer = Global.getSector().playerPerson
            else {
                Console.showMessage("Error: This command can only be used in the campaign or refit tab.")
            }
        }

        if (officer == null) {
            val member = ReflectionMisc.getCurrentMemberInRefitTab()
            if (member == null) {
                Console.showMessage("Failed to get member in refit screen")
                return BaseCommand.CommandResult.ERROR
            }
            if (member.captain == null || member.captain.isDefault) {
                Console.showMessage("Ship has no officer to add xp to")
                return BaseCommand.CommandResult.ERROR
            }
            if (member.captain.isAICore) {
                Console.showMessage("Cannot add xp to an AI core")
                return BaseCommand.CommandResult.ERROR
            }

            officer = member.captain
        }

        val playerPlugin = if (officer.isPlayer)
            Global.getSettings().levelupPlugin
        else
            null

        val officerPlugin = if (!officer.isPlayer)
            (Global.getSettings().getPlugin("officerLevelUp") as OfficerLevelupPlugin)
        else
            null

        val amount: Long
        if (args.isEmpty()) {
            if (playerPlugin != null)
                amount = max(0L, playerPlugin.getXPForLevel(min(officer.getMaxOfficerLevel(), officer.stats.level + 1)) - officer.stats.xp)
            else
                amount = max(0L, officerPlugin!!.getXPForLevel(min(officer.getMaxOfficerLevel(), officer.stats.level + 1)) - officer.stats.xp)
        } else {
            if (!CommandUtils.isLong(args)) {
                Console.showMessage("Error: experience must be a whole number!")
                return CommandResult.BAD_SYNTAX
            }

            amount = args.toLong()
        }

        if (amount >= 0L) {
            val added = if (playerPlugin != null) {
                val added = min(amount, playerPlugin.getXPForLevel(playerPlugin.maxLevel))
                Console.showMessage("Added " + CommandUtils.format(added.toFloat()) + " experience points to player.")
                added
            } else {
                val added = min(amount, officerPlugin!!.getXPForLevel(officer.getMaxOfficerLevel()))
                Console.showMessage("Added " + CommandUtils.format(added.toFloat()) + " experience points to officer.")
                added
            }
            if (playerPlugin != null)
                officer.stats.addXP(added)
            else
                Global.getSector().playerFleet.fleetData.getOfficerData(officer).addXP(added)
        } else {
            val removed = min(-amount, officer.stats.getXP())
            if (playerPlugin != null)
                Console.showMessage("Removed " + CommandUtils.format(removed.toFloat()) + " experience points from player.")
            else
                Console.showMessage("Removed " + CommandUtils.format(removed.toFloat()) + " experience points from officer.")

            officer.stats.addXP(-removed)
        }

        return BaseCommand.CommandResult.SUCCESS
    }
}