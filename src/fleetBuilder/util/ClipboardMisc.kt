package fleetBuilder.util

import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.config.ModSettings.getDefaultExcludeVariantTags
import fleetBuilder.persistence.member.JSONMember.saveMemberToJson
import fleetBuilder.persistence.variant.CompressedVariant.saveVariantToCompString
import fleetBuilder.persistence.variant.JSONVariant.saveVariantToJson
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.util.FBMisc.extractDataFromString
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import fleetBuilder.util.lib.ClipboardUtil.getClipboardJSONFileContents
import fleetBuilder.util.lib.ClipboardUtil.getClipboardTextSafe
import fleetBuilder.util.lib.ClipboardUtil.setClipboardText
import fleetBuilder.variants.VariantLib
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClipboardMisc {

    fun saveVariantToClipboard(variant: ShipVariantAPI, shift: Boolean = false) {
        if (variant.hasHullMod(commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        val variantToSave = variant.clone()
        variantToSave.hullVariantId = VariantLib.makeVariantID(variantToSave)

        if (!shift) {
            val comp = saveVariantToCompString(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = getDefaultExcludeVariantTags()
                }
            )
            setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("compressed_variant_copied_to_clipboard"))
        } else {
            val json = saveVariantToJson(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = getDefaultExcludeVariantTags()
                }
            )
            setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard"))
        }
    }

    fun saveMemberToClipboard(member: FleetMemberAPI, shift: Boolean = false) {
        if (member.variant.hasHullMod(commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        if (shift) {
            //val comp = saveMemberToCompString(member)
            //setClipboardText(comp)
            //DisplayMessage.showMessage("Member compressed and copied to clipboard")
            DisplayMessage.showMessage("Copying the compressed member is currently unimplemented. Please avoid holding shift.", Color.YELLOW)
        } else {
            val json = saveMemberToJson(member)
            setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("fleet_member_copied_to_clipboard"))
        }
    }

    fun codexEntryToClipboard(codex: CodexDialog) {
        val param = getCodexEntryParam(codex) ?: return

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

    fun extractDataFromClipboard(): Any? {
        val contents = getClipboardJSONFileContents()
        val clipboardText = contents ?: getClipboardTextSafe() ?: return null

        return extractDataFromString(clipboardText)
    }
}