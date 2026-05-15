package fleetBuilder.serialization

import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.core.FBConst
import fleetBuilder.core.FBTxt
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.DataFleet.getFleetDataFromFleet
import fleetBuilder.serialization.fleet.FleetSettings
import fleetBuilder.serialization.fleet.JSONFleet
import fleetBuilder.serialization.member.CompressedMember
import fleetBuilder.serialization.member.DataMember
import fleetBuilder.serialization.member.DataMember.getMemberDataFromMember
import fleetBuilder.serialization.member.JSONMember
import fleetBuilder.serialization.member.MemberSettings
import fleetBuilder.serialization.person.CompressedPerson
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.serialization.person.DataPerson.getPersonDataFromPerson
import fleetBuilder.serialization.person.JSONPerson
import fleetBuilder.serialization.person.PersonSettings
import fleetBuilder.serialization.variant.CompressedVariant
import fleetBuilder.serialization.variant.DataVariant
import fleetBuilder.serialization.variant.DataVariant.getVariantDataFromVariant
import fleetBuilder.serialization.variant.JSONVariant
import fleetBuilder.serialization.variant.VariantSettings
import fleetBuilder.util.LookupUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.createHullVariant
import fleetBuilder.util.lib.ClipboardUtil
import org.json.JSONObject
import org.lwjgl.input.Keyboard
import java.awt.Color

object ClipboardMisc {

    @JvmOverloads
    fun saveVariantToClipboard(
        variant: ShipVariantAPI,
        shift: Boolean = false,
        settings: VariantSettings = VariantSettings()
    ): Boolean {
        val data = getVariantDataFromVariant(variant, filterParsed = false) // The filtering comes later
        return saveVariantToClipboard(data, shift, settings)
    }

    @JvmOverloads
    fun saveVariantToClipboard(
        variant: DataVariant.ParsedVariantData,
        shift: Boolean = false,
        settings: VariantSettings = VariantSettings()
    ): Boolean {
        val settings = settings.apply {
            includeDefaultJSON = true
            excludeTagsWithID += FBConst.DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY
        }

        val dataVariant = DataVariant.filterParsedVariantData(variant, settings)//.copy(variantId = VariantUtils.makeVariantID(variant.hullId, variant.displayName))

        if (dataVariant.tags.contains(FBConst.NO_COPY_TAG) || LookupUtils.getHullSpec(dataVariant.hullId)?.hasTag(FBConst.NO_COPY_TAG) == true) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedVariant.saveVariantToCompString(
                dataVariant
            )
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard_compressed"))
        } else {
            val json = JSONVariant.saveVariantToJson(
                dataVariant, settings
            )
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("variant_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun savePersonToClipboard(
        person: PersonAPI,
        shift: Boolean = false,
        settings: PersonSettings = PersonSettings()
    ): Boolean {
        if (person.isDefault) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_default_officer"), Color.YELLOW)
            return false
        }
        val data = getPersonDataFromPerson(person, settings)
        return savePersonToClipboard(data, shift)
    }

    @JvmOverloads
    fun savePersonToClipboard(
        person: DataPerson.ParsedPersonData,
        shift: Boolean = false,
        settings: PersonSettings? = null
    ): Boolean {
        val dataPerson = if (settings != null)
            DataPerson.filterParsedPersonData(person, settings)
        else
            person

        if (dataPerson.tags.contains(FBConst.NO_COPY_TAG)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedPerson.savePersonToCompString(dataPerson)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("officer_copied_to_clipboard_compressed"))
        } else {
            val json = JSONPerson.savePersonToJson(dataPerson)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("officer_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun saveMemberToClipboard(
        member: FleetMemberAPI,
        shift: Boolean = false,
        settings: MemberSettings = MemberSettings()
    ): Boolean {
        val data = getMemberDataFromMember(member, settings)
        return saveMemberToClipboard(data, shift)
    }

    @JvmOverloads
    fun saveMemberToClipboard(
        member: DataMember.ParsedMemberData,
        shift: Boolean = false,
        settings: MemberSettings? = null
    ): Boolean {
        val dataMember = if (settings != null)
            DataMember.filterParsedMemberData(member, settings)
        else
            member

        if (dataMember.variantData != null && (dataMember.variantData.tags.contains(FBConst.NO_COPY_TAG) || LookupUtils.getHullSpec(dataMember.variantData.hullId)?.hasTag(FBConst.NO_COPY_TAG) == true)) {
            DisplayMessage.showMessage(FBTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedMember.saveMemberToCompString(dataMember)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("member_copied_to_clipboard_compressed"))
        } else {
            val json = JSONMember.saveMemberToJson(dataMember)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FBTxt.txt("member_copied_to_clipboard"))
        }

        return true
    }

    @JvmOverloads
    fun saveFleetToClipboard(
        fleet: FleetDataAPI,
        shift: Boolean = false,
        settings: FleetSettings = FleetSettings()
    ): Boolean {
        val data = getFleetDataFromFleet(fleet, settings)
        return saveFleetToClipboard(data, shift)
    }

    @JvmOverloads
    fun saveFleetToClipboard(
        fleet: DataFleet.ParsedFleetData,
        shift: Boolean = false,
        settings: FleetSettings? = null
    ): Boolean {
        val dataFleet = if (settings != null)
            DataFleet.filterParsedFleetData(fleet, settings)
        else
            fleet

        if (shift) {
            val comp = CompressedFleet.saveFleetToCompString(dataFleet)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FBTxt.txt("copied_entire_fleet_to_clipboard_compressed"))
        } else {
            val json = JSONFleet.saveFleetToJson(dataFleet)
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