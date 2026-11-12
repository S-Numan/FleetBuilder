package fleetkit.serialization

import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.codex.CodexDialogAPI
import fleetkit.core.config.FTVariables
import fleetkit.core.util.DisplayMessage
import fleetkit.core.util.FTTxt
import fleetkit.serialization.fleet.CompressedFleet
import fleetkit.serialization.fleet.DataFleet
import fleetkit.serialization.fleet.DataFleet.getFleetDataFromFleet
import fleetkit.serialization.fleet.FleetSettings
import fleetkit.serialization.fleet.JSONFleet
import fleetkit.serialization.member.CompressedMember
import fleetkit.serialization.member.DataMember
import fleetkit.serialization.member.DataMember.getMemberDataFromMember
import fleetkit.serialization.member.JSONMember
import fleetkit.serialization.member.MemberSettings
import fleetkit.serialization.person.CompressedPerson
import fleetkit.serialization.person.DataPerson
import fleetkit.serialization.person.DataPerson.getPersonDataFromPerson
import fleetkit.serialization.person.JSONPerson
import fleetkit.serialization.person.PersonSettings
import fleetkit.serialization.variant.CompressedVariant
import fleetkit.serialization.variant.DataVariant
import fleetkit.serialization.variant.DataVariant.getVariantDataFromVariant
import fleetkit.serialization.variant.JSONVariant
import fleetkit.serialization.variant.VariantSettings
import fleetkit.util.lib.ClipboardUtil
import org.json.JSONObject
import org.lwjgl.input.Keyboard
import org.magiclib.util.MagicLookup
import org.magiclib.util.api.createHullVariant
import org.magiclib.util.reflection.boxed.BoxedCodexDialog
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
            excludeTagsWithID += FTVariables.DEFAULT_EXCLUDE_TAGS_ON_VARIANT_COPY
        }

        val dataVariant = DataVariant.filterParsedVariantData(variant, settings)//.copy(variantId = VariantUtils.makeVariantID(variant.hullId, variant.displayName))

        if (dataVariant.tags.contains(FTVariables.NO_COPY_TAG) || MagicLookup.getHullSpec(dataVariant.hullId)?.hasTag(FTVariables.NO_COPY_TAG) == true) {
            DisplayMessage.showMessage(FTTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedVariant.saveVariantToCompString(
                dataVariant
            )
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FTTxt.txt("variant_copied_to_clipboard_compressed"))
        } else {
            val json = JSONVariant.saveVariantToJson(
                dataVariant, settings
            )
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FTTxt.txt("variant_copied_to_clipboard"))
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
            DisplayMessage.showMessage(FTTxt.txt("no_copy_default_officer"), Color.YELLOW)
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

        if (dataPerson.tags.contains(FTVariables.NO_COPY_TAG)) {
            DisplayMessage.showMessage(FTTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedPerson.savePersonToCompString(dataPerson)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FTTxt.txt("officer_copied_to_clipboard_compressed"))
        } else {
            val json = JSONPerson.savePersonToJson(dataPerson)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FTTxt.txt("officer_copied_to_clipboard"))
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

        if (dataMember.variantData != null && (dataMember.variantData.tags.contains(FTVariables.NO_COPY_TAG) || MagicLookup.getHullSpec(dataMember.variantData.hullId)?.hasTag(FTVariables.NO_COPY_TAG) == true)) {
            DisplayMessage.showMessage(FTTxt.txt("no_copy_no_copy_tag"), Color.YELLOW)
            return false
        }

        if (!shift) {
            val comp = CompressedMember.saveMemberToCompString(dataMember)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FTTxt.txt("member_copied_to_clipboard_compressed"))
        } else {
            val json = JSONMember.saveMemberToJson(dataMember)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FTTxt.txt("member_copied_to_clipboard"))
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

        if (!shift) {
            val comp = CompressedFleet.saveFleetToCompString(dataFleet)
            ClipboardUtil.setClipboardText(comp)
            DisplayMessage.showMessage(FTTxt.txt("copied_entire_fleet_to_clipboard_compressed"))
        } else {
            val json = JSONFleet.saveFleetToJson(dataFleet)
            ClipboardUtil.setClipboardText(json.toString(4))
            DisplayMessage.showMessage(FTTxt.txt("copied_entire_fleet_to_clipboard"))
        }

        return true
    }

    fun codexEntryToClipboard(codex: CodexDialogAPI) {
        val param = BoxedCodexDialog.get(codex)?.getEntryParam() ?: return

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