package fleetBuilder.consoleCommands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Industries
import fleetBuilder.features.CommanderShuttle.addPlayerShuttle
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console
import org.lazywizard.console.commands.*


class BuildFleet : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCampaign) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val playerFleet = Global.getSector().playerFleet.fleetData

        Console.showMessage("Removing officers")
        for (officer in playerFleet.officersCopy) playerFleet.removeOfficer(officer.person)
        Console.showMessage("Removing fleet members")
        for (member in playerFleet.membersListCopy) playerFleet.removeFleetMember(member)
        Console.showMessage("Removing cargo")
        playerFleet.fleet.cargo.clear()
        playerFleet.fleet.cargo.addSupplies(1f)
        playerFleet.fleet.cargo.addCrew(1)
        playerFleet.fleet.cargo.addFuel(1f)

        addPlayerShuttle()

        Jump().runCommand("corvus", context)
        GoTo().runCommand("corvus_abandoned_station", context)

        //Give the corvus abandoned station a spaceport
        Global.getSector().getStarSystem("corvus").getEntityById("corvus_abandoned_station").market.addIndustry(
            Industries.SPACEPORT
        )
        AddCredits().runCommand("30000000", context)
        AddXP().runCommand("30000000", context)
        AddStoryPoints().runCommand("500", context)
        AllCommodities().runCommand("", context)
        AllHullmods().runCommand("", context)
        AllWeapons().runCommand("", context)
        AllWings().runCommand("", context)

        Console.showMessage("\nFleet builder done")

        return BaseCommand.CommandResult.SUCCESS
    }
}