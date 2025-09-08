package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.config.FBTxt
import fleetBuilder.config.ModSettings
import fleetBuilder.config.ModSettings.commandShuttleId
import fleetBuilder.config.ModSettings.getDefaultExcludeVariantTags
import fleetBuilder.persistence.fleet.JSONFleet.extractFleetDataFromJson
import fleetBuilder.persistence.member.JSONMember.extractMemberDataFromJson
import fleetBuilder.persistence.member.JSONMember.saveMemberToJson
import fleetBuilder.persistence.person.JSONPerson.extractPersonDataFromJson
import fleetBuilder.persistence.variant.CompressedVariant.extractVariantDataFromCompString
import fleetBuilder.persistence.variant.CompressedVariant.saveVariantToCompString
import fleetBuilder.persistence.variant.JSONVariant.extractVariantDataFromJson
import fleetBuilder.persistence.variant.JSONVariant.saveVariantToJson
import fleetBuilder.persistence.variant.VariantSettings
import fleetBuilder.util.ClipboardUtil.cleanJsonStringInput
import fleetBuilder.util.ClipboardUtil.getClipboardJSONFileContents
import fleetBuilder.util.ClipboardUtil.getClipboardTextSafe
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import fleetBuilder.variants.VariantLib
import org.json.JSONObject
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClipboardMisc {

    fun saveVariantToClipboard(variant: ShipVariantAPI, compress: Boolean = false) {
        if (variant.hasHullMod(commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        val variantToSave = variant.clone()
        variantToSave.hullVariantId = VariantLib.makeVariantID(variantToSave)

        if (compress) {
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

    fun saveMemberToClipboard(member: FleetMemberAPI, compress: Boolean = false) {
        if (member.variant.hasHullMod(commandShuttleId)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_command_shuttle"), Color.YELLOW)
            return
        }

        if (compress) {
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
                val emptyVariant = Global.getSettings().createEmptyVariant(param.hullId, param)
                saveVariantToClipboard(emptyVariant, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            }

            is FleetMemberAPI -> {
                saveMemberToClipboard(param, Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
            }
        }
    }

    fun extractDataFromClipboard(): Any? {
        val contents = getClipboardJSONFileContents()
        var clipboardText = contents ?: getClipboardTextSafe() ?: return null

        if (clipboardText.isEmpty()) return null

        if (clipboardText.startsWithJsonBracket()) {
            clipboardText = cleanJsonStringInput(clipboardText)

            val json = try {
                JSONObject(clipboardText)
            } catch (_: Exception) {
                null
            } ?: return null

            return when {
                json.has("skills") -> {
                    // Officer
                    extractPersonDataFromJson(json)
                }

                json.has("variant") || json.has("officer") -> {
                    // Fleet member
                    extractMemberDataFromJson(json)
                }

                json.has("hullId") -> {
                    // Variant
                    extractVariantDataFromJson(json)
                }

                json.has("members") -> {
                    // Fleet
                    extractFleetDataFromJson(json)
                }

                else -> {
                    null
                }
            }


        } else {
            val data = extractVariantDataFromCompString(clipboardText)
            if (data != null) {
                return data
            }
        }

        return null
    }
}