package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import fleetBuilder.core.ModSettings
import fleetBuilder.core.ModSettings.commandShuttleId
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.features.commanderShuttle.CommanderShuttle
import fleetBuilder.serialization.member.JSONMember.saveMemberToJson
import fleetBuilder.serialization.person.JSONPerson.savePersonToJson
import fleetBuilder.util.FBTxt
import fleetBuilder.util.lib.ClipboardUtil
import java.awt.Color

object ClipboardHotkeyHandlerUtils {
    fun personClick(event: InputEventAPI, person: PersonAPI) {
        if (event.isCtrlDown) {
            if (event.isLMBDownEvent) {
                val json = savePersonToJson(person)
                ClipboardUtil.setClipboardText(json.toString(4))
                DisplayMessage.showMessage(FBTxt.txt("officer_copied_to_clipboard"))
                event.consume()
            }
        } else if (person.isPlayer) {
            val isShuttle = Global.getSector().playerFleet.fleetData.membersListCopy.find { it.captain === person }?.variant?.hasHullMod(commandShuttleId) == true

            if (event.isLMBDownEvent && isShuttle) { // Eat attempt to open captain picker dialog for shuttle. The shuttle is player only
                event.consume()
                return
            }

            if (event.isRMBDownEvent) {
                when {
                    isShuttle -> {
                        if (Global.getSector()?.playerFleet?.fleetSizeCount == 1)
                            DisplayMessage.showMessage(FBTxt.txt("cannot_remove_last_ship_in_fleet"), Color.YELLOW)
                        else
                            CommanderShuttle.removePlayerShuttle()
                    }

                    ModSettings.unassignPlayer() -> {
                        CommanderShuttle.addPlayerShuttle()
                    }

                    else -> {
                        DisplayMessage.showMessage(
                            FBTxt.txt("enable_unassign_player", ModSettings.modName),
                            Color.YELLOW
                        )
                    }
                }

                Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
                event.consume()
            }
        }
    }

    fun memberClick(event: InputEventAPI, member: FleetMemberAPI) {
        if (member.variant.hasHullMod(commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        val json = saveMemberToJson(member)
        ClipboardUtil.setClipboardText(json.toString(4))
        DisplayMessage.showMessage(FBTxt.txt("fleet_member_copied_to_clipboard"))
        event.consume()
    }
}