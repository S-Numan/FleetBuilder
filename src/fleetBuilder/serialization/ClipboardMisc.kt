package fleetBuilder.serialization

import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.core.FBSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.JSONFleet
import fleetBuilder.serialization.member.CompressedMember
import fleetBuilder.serialization.member.JSONMember
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.serialization.person.JSONPerson
import fleetBuilder.serialization.variant.CompressedVariant
import fleetBuilder.serialization.variant.JSONVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.FBTxt
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.createHullVariant
import fleetBuilder.util.lib.ClipboardUtil
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClipboardMisc {

    @JvmOverloads
    fun saveVariantToClipboard(variant: ShipVariantAPI, shift: Boolean = false): Boolean {
        if (variant.hasHullMod(FBSettings.commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return false
        }

        val variantToSave = variant.clone()
        variantToSave.hullVariantId = VariantUtils.makeVariantID(variantToSave)

        if (!shift) {
            val comp = CompressedVariant.saveVariantToCompString(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = FBSettings.getDefaultExcludeVariantTags()
                }
            )
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard_compressed"))
        } else {
            val json = JSONVariant.saveVariantToJson(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = FBSettings.getDefaultExcludeVariantTags()
                }
            )
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun savePersonToClipboard(person: PersonAPI, shift: Boolean = false): Boolean {
        if (person.isDefault) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_default_officer"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedPerson.savePersonToCompString(person)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("officer_copied_to_clipboard_compressed"))
        } else {
            val json = JSONPerson.savePersonToJson(person)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("officer_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun saveMemberToClipboard(member: FleetMemberAPI, shift: Boolean = false): Boolean {
        if (member.variant.hasHullMod(FBSettings.commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedMember.saveMemberToCompString(member)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("member_copied_to_clipboard_compressed"))
        } else {
            val json = JSONMember.saveMemberToJson(member)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("member_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun saveFleetToClipboard(fleet: FleetDataAPI, shift: Boolean = false): Boolean {
        if (!shift) {
            val comp = CompressedFleet.saveFleetToCompString(fleet)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("copied_entire_fleet_to_clipboard_compressed"))
        } else {
            val json = JSONFleet.saveFleetToJson(fleet)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("copied_entire_fleet_to_clipboard"))
        }

        return true
    }

    fun codexEntryToClipboard(codex: CodexDialog) {
        val param = ReflectionMisc.getCodexEntryParam(codex) ?: return

        when (param) {
            is ShipHullSpecAPI -> {
                val emptyVariant = param.createHullVariant()
                saveVariantToClipboard(emptyVariant, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            }

            is FleetMemberAPI -> {
                saveMemberToClipboard(param, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            }
        }
    }

    @JvmOverloads
    fun extractDataFromClipboard(missing: MissingContent = MissingContent()): Any? {
        val jsonContents = ClipboardUtil.getClipboardJson()
        if (jsonContents != null)
            return SerializationUtils.extractDataFromJSON(jsonContents, missing)

        val stringContents = ClipboardUtil.getClipboardTextSafe()
        if (stringContents != null)
            return SerializationUtils.extractDataFromString(stringContents, missing)

        return null
    }
}