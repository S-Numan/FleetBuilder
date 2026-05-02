package fleetBuilder.serialization

import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.core.FBConst
import fleetBuilder.core.FBSettings
import fleetBuilder.core.FBTxt
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
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.api.kotlin.createHullVariant
import fleetBuilder.util.api.kotlin.getActualHull
import fleetBuilder.util.api.kotlin.getCompatibleDLessHull
import fleetBuilder.util.api.kotlin.getEffectiveHull
import fleetBuilder.util.lib.ClipboardUtil
import org.json.JSONObject
import org.lazywizard.console.Console
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClipboardMisc {

    @JvmOverloads
    fun saveVariantToClipboard(variant: ShipVariantAPI, shift: Boolean = false): Boolean {
        if (variant.hasTag(FBConst.NO_COPY_TAG) || variant.hullSpec.hasTag(FBConst.NO_COPY_TAG)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (FBSettings.showDebug) {
            val hull = variant.hullSpec
            val hullID = hull.hullId
            val actual = hull.getActualHull().hullId
            val dlesscompatible = hull.getCompatibleDLessHull().hullId
            val effective = hull.getEffectiveHull().hullId

            if (FBSettings.isConsoleModEnabled) {
                Console.showMessage(
                    "Hull: $hullID\nActual: $actual\nDless compatible: $dlesscompatible\nEffective: $effective"
                )
            }
        }

        val variantToSave = variant.clone()
        variantToSave.hullVariantId = VariantUtils.makeVariantID(variantToSave)

        if (!shift) {
            val comp = CompressedVariant.saveVariantToCompString(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = FBConst.DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY.toMutableSet()
                }
            )
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard_compressed"))
        } else {
            val json = JSONVariant.saveVariantToJson(
                variantToSave,
                VariantSettings().apply {
                    excludeTagsWithID = FBConst.DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY.toMutableSet()
                }
            )
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun savePersonToClipboard(person: PersonAPI, shift: Boolean = false): Boolean {
        if (person.hasTag(FBConst.NO_COPY_TAG)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }
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
        if (member.variant.hasTag(FBConst.NO_COPY_TAG) || member.variant.hullSpec.hasTag(FBConst.NO_COPY_TAG)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
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
        if (shift) {
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
        val contents = ClipboardUtil.getClipboardContentsAutoJSON() ?: return null

        if (contents is JSONObject)
            return SerializationUtils.extractDataFromJSON(contents, missing)

        if (contents is String)
            return SerializationUtils.extractDataFromString(contents, missing)

        return null
    }
}