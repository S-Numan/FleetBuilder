package fleetBuilder.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.persistence.fleet.FleetSerialization
import fleetBuilder.persistence.member.MemberSerialization
import fleetBuilder.persistence.member.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.person.PersonSerialization
import fleetBuilder.persistence.variant.VariantSerialization
import fleetBuilder.persistence.variant.VariantSerialization.saveVariantToJson
import fleetBuilder.util.ClipboardUtil.cleanJsonStringInput
import fleetBuilder.util.ClipboardUtil.getClipboardJSONFileContents
import fleetBuilder.util.ClipboardUtil.getClipboardTextSafe
import fleetBuilder.util.ClipboardUtil.setClipboardText
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import org.json.JSONObject

object ClipboardMisc {
    fun codexEntryToClipboard(codex: CodexDialog) {
        val param = getCodexEntryParam(codex)
        if (param == null) return

        when (param) {
            is ShipHullSpecAPI -> {
                val emptyVariant = Global.getSettings().createEmptyVariant(param.hullId, param)
                val json = saveVariantToJson(emptyVariant)
                setClipboardText(json.toString(4))
                showMessage("Copied codex variant to clipboard")
            }

            is FleetMemberAPI -> {
                val json = saveMemberToJson(param)
                setClipboardText(json.toString(4))
                showMessage("Copied codex member to clipboard")
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
                    PersonSerialization.extractPersonDataFromJson(json)
                }

                json.has("variant") || json.has("officer") -> {
                    // Fleet member
                    MemberSerialization.extractMemberDataFromJson(json)
                }

                json.has("hullId") -> {
                    // Variant
                    VariantSerialization.extractVariantDataFromJson(json)
                }

                json.has("members") -> {
                    // Fleet
                    FleetSerialization.extractFleetDataFromJson(json)
                }

                else -> {
                    null
                }
            }


        } else {
            val data = VariantSerialization.extractVariantDataFromCompString(clipboardText)
            if (data != null) {
                return data
            }
        }

        return null
    }
}