package fleetBuilder.temporary

import MagicLib.findChildWithMethod
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.persistence.OfficerSerialization.saveOfficerToJson
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.MISC
import fleetBuilder.util.getActualCurrentTab
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console
import starficz.ReflectionUtils

class CopyOfficer : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }
        val sector = Global.getSector() ?: return BaseCommand.CommandResult.ERROR
        val ui = sector.campaignUI ?: return BaseCommand.CommandResult.ERROR

        if (ui.getActualCurrentTab() != CoreUITabId.REFIT) {
            Console.showMessage("Must be in refit screen")
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val refitTab = MISC.getRefitTab() ?: return BaseCommand.CommandResult.ERROR

        val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI
            ?: return BaseCommand.CommandResult.ERROR
        val fleetMember =
            ReflectionUtils.invoke(refitPanel, "getMember") as? FleetMemberAPI ?: return BaseCommand.CommandResult.ERROR

        val officer = fleetMember.captain

        if (officer.isDefault) {
            Console.showMessage("No officer to copy")
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val json = saveOfficerToJson(officer)

        setClipboardText(json.toString(4))

        Console.showMessage("Copied officer to clipboard")

        return BaseCommand.CommandResult.SUCCESS
    }
}