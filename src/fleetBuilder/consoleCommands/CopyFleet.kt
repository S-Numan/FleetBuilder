package fleetBuilder.consoleCommands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import fleetBuilder.persistence.FleetSerialization
import fleetBuilder.persistence.MemberSerialization
import fleetBuilder.persistence.PersonSerialization
import fleetBuilder.util.ClipboardUtil
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console

class CopyFleet : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (!context.isInCombat) {
            Console.showMessage(CommonStrings.ERROR_COMBAT_ONLY)
            return BaseCommand.CommandResult.WRONG_CONTEXT
        }

        val engine = Global.getCombatEngine()

        val playerMgr = engine.getFleetManager(0)
        val enemyMgr = engine.getFleetManager(1)

        // Choose fleet manager based on input args
        val mgr = when (args.lowercase().trim()) {
            "player" -> playerMgr
            "0" -> playerMgr
            "enemy" -> enemyMgr
            "1" -> enemyMgr
            else -> {
                Console.showMessage("Invalid argument. Use 'player' or 'enemy'.")
                return BaseCommand.CommandResult.BAD_SYNTAX
            }
        }

        val commander = mgr.fleetCommander
        val allDeployed = mgr.allEverDeployedCopy.map { it.member }
        val allReserves = mgr.reservesCopy

        val allShips: Map<String, FleetMemberAPI> =
            (allDeployed + allReserves).associateBy { member: FleetMemberAPI -> member.id }

        val fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, FleetTypes.TASK_FORCE, false)

        var aggression = 2

        var hasFlagship = false
        allShips.forEach { (id, tempMember) ->
            val jsonMember = MemberSerialization.saveMemberToJson(tempMember)
            val member = MemberSerialization.getMemberFromJson(jsonMember)
            fleet.fleetData.addFleetMember(member)

            if (member.captain.id == commander.id) {
                member.isFlagship = true
                fleet.commander = member.captain
                hasFlagship = true
            } else {
                member.isFlagship = false

                if (member.captain.isDefault) {
                    if (member.captain.personalityAPI.id == Personalities.CAUTIOUS)
                        aggression = 1
                    else if (member.captain.personalityAPI.id == Personalities.STEADY)
                        aggression = 2
                    else if (member.captain.personalityAPI.id == Personalities.AGGRESSIVE)
                        aggression = 3
                    else if (member.captain.personalityAPI.id == Personalities.RECKLESS)
                        aggression = 5
                }
            }
        }

        if (!hasFlagship)
            fleet.commander = PersonSerialization.getPersonFromJson(PersonSerialization.savePersonToJson(commander))


        val json = FleetSerialization.saveFleetToJson(
            fleet,
            FleetSerialization.FleetSettings().apply {
                includeAggression = false
            })
        json.put("aggression_doctrine", aggression)

        ClipboardUtil.setClipboardText(json.toString(4))

        Console.showMessage("Commander: ${commander?.name?.fullName}")
        Console.showMessage("Deployed ships: ${allDeployed.size}")
        Console.showMessage("Reserve ships: ${allReserves.size}")
        Console.showMessage("Copied to clipboard")


        return BaseCommand.CommandResult.SUCCESS
    }
}