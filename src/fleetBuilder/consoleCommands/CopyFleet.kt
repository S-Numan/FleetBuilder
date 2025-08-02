package fleetBuilder.consoleCommands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Personalities
import com.fs.starfarer.api.mission.FleetSide
import com.fs.starfarer.api.mission.MissionDefinitionAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.fleet.FleetMember
import fleetBuilder.persistence.fleet.FleetSerialization
import fleetBuilder.persistence.member.MemberSerialization
import fleetBuilder.persistence.person.PersonSerialization
import fleetBuilder.util.ClipboardUtil
import fleetBuilder.util.ReflectionMisc
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.CommonStrings
import org.lazywizard.console.Console
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke

class CopyFleet : BaseCommand {
    override fun runCommand(args: String, context: BaseCommand.CommandContext): BaseCommand.CommandResult {
        if (context.isInCombat) {
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
        } else if (context.isInMainMenu) {
            val coreUI = ReflectionMisc.getCoreUI() ?: return BaseCommand.CommandResult.ERROR

            val missionThing = (coreUI.invoke("getChildrenCopy") as List<*>).find { it?.getMethodsMatching(name = "getMissionList")?.isNotEmpty() == true }
            val missionDetail = missionThing?.invoke("getMissionDetail") as? UIPanelAPI
                ?: return BaseCommand.CommandResult.ERROR

            val api = missionDetail.invoke("getPreview") as? MissionDefinitionAPI
                ?: return BaseCommand.CommandResult.ERROR

            val fleetSidePlayer = api.invoke("getFleet", FleetSide.PLAYER)
            val fleetSideEnemy = api.invoke("getFleet", FleetSide.ENEMY)

            val side = when (args.lowercase().trim()) {
                "player" -> fleetSidePlayer
                "0" -> fleetSidePlayer
                "enemy" -> fleetSideEnemy
                "1" -> fleetSideEnemy
                else -> {
                    return BaseCommand.CommandResult.BAD_SYNTAX
                }
            }

            val membersMethod = side?.getMethodsMatching(returnType = List::class.java)
            val members = membersMethod?.getOrNull(0)?.invoke(side) as? List<*>
            val fleetData = (members?.getOrNull(0) as? FleetMember)?.fleetData ?: return BaseCommand.CommandResult.ERROR

            var aggression = 2

            fleetData.membersListCopy.forEach { member ->
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

            val json = FleetSerialization.saveFleetToJson(
                fleetData,
                FleetSerialization.FleetSettings().apply {
                    includeAggression = false
                })
            json.put("aggression_doctrine", aggression)

            ClipboardUtil.setClipboardText(json.toString(4))

            Console.showMessage("Commander: ${fleetData.commander?.name?.fullName}")
            Console.showMessage("Ships: ${fleetData.membersListCopy.size}")
            Console.showMessage("Copied to clipboard")

            return BaseCommand.CommandResult.SUCCESS
        }

        Console.showMessage("Context not supported")
        return BaseCommand.CommandResult.WRONG_CONTEXT
    }
}