package fleetBuilder.temporary

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import fleetBuilder.misc.ClipboardUtil.getClipboardJson
import fleetBuilder.util.MISC
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console


class AddFleet : BaseCommand {

    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val json = getClipboardJson()
        if(json == null) {
            Console.showMessage("No valid fleet data found in clipboard")
            return BaseCommand.CommandResult.ERROR
        }
        val fleet = MISC.createFleetFromJson(json, faction = Factions.PIRATES)
        fleet.memoryWithoutUpdate[MemFlags.FLEET_FIGHT_TO_THE_LAST] = true

        if(fleet.fleetSizeCount == 0) {
            Console.showMessage("Failed to create fleet from clipboard")
            return BaseCommand.CommandResult.ERROR
        }

        val playerFleet = Global.getSector().playerFleet
        playerFleet.containingLocation.spawnFleet(playerFleet, 0f, 0f, fleet)

        Global.getSector().campaignUI.showInteractionDialog(fleet)

        Console.showMessage("Fleet from clipboard added to campaign")

        return BaseCommand.CommandResult.SUCCESS
    }
}