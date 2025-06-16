package fleetBuilder.misc


import MagicLib.findChildWithMethod
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.ModSettings
import fleetBuilder.misc.Clipboard.setClipboardText
import fleetBuilder.misc.MISC.getCodexEntryParam
import fleetBuilder.misc.MISC.showMessage
import fleetBuilder.serialization.MemberSerialization.saveMemberToJson
import fleetBuilder.serialization.VariantSerialization.saveVariantToJson
import fleetBuilder.variants.VariantLib.makeVariantID
import starficz.ReflectionUtils

object ClipboardStuff {

    fun refitScreenVariantToClipboard(): Boolean {
        val refitTab = MISC.getRefitTab() ?: return false

        val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI ?: return false
        val shipDisplay = ReflectionUtils.invoke(refitPanel, "getShipDisplay") as? UIPanelAPI ?: return false
        val baseVariant = ReflectionUtils.invoke(shipDisplay, "getCurrentVariant") as? HullVariantSpec ?: return false

        val variantToSave = baseVariant.clone()
        variantToSave.hullVariantId = makeVariantID(baseVariant)

        //setClipboardText(saveVariantToCompString(variantToSave, includeDMods = ModSettings.saveDMods, applySMods = ModSettings.saveSMods))

        val json = saveVariantToJson(variantToSave, includeDMods = ModSettings.saveDMods, applySMods = ModSettings.saveSMods)

        setClipboardText(json.toString(4))
        return true
    }

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
}