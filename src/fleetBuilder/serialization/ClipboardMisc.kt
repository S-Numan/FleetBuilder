package fleetBuilder.serialization

import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.member.JSONMember
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

    fun saveVariantToClipboard(variant: ShipVariantAPI, shift: Boolean = false) {
        if (variant.hasHullMod(ModSettings.commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        val variantToSave = variant.clone()
        variantToSave.hullVariantId = VariantUtils.makeVariantID(variantToSave)

        if (!shift) {
            val comp = CompressedVariant.saveVariantToCompString(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = ModSettings.getDefaultExcludeVariantTags()
                }
            )
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("compressed_variant_copied_to_clipboard"))
        } else {
            val json = JSONVariant.saveVariantToJson(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = ModSettings.getDefaultExcludeVariantTags()
                }
            )
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard"))
        }
    }

    fun saveMemberToClipboard(member: FleetMemberAPI, shift: Boolean = false) {
        if (member.variant.hasHullMod(ModSettings.commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        if (shift) {
            //val comp = saveMemberToCompString(member)
            //setClipboardText(comp)
            //DisplayMessage.showMessage("Member compressed and copied to clipboard")
            DisplayMessage.showMessage("Copying the compressed member is currently unimplemented. Please avoid holding shift.", Color.YELLOW)
        } else {
            val json = JSONMember.saveMemberToJson(member)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("fleet_member_copied_to_clipboard"))
        }
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

    fun extractDataFromClipboard(): Any? {
        val jsonContents = ClipboardUtil.getClipboardJson()
        if (jsonContents != null)
            return SerializationUtils.extractDataFromJSON(jsonContents)

        val stringContents = ClipboardUtil.getClipboardTextSafe()
        if (stringContents != null)
            return SerializationUtils.extractDataFromString(stringContents)

        return null
    }
}