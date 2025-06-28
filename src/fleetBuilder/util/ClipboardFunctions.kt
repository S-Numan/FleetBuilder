package fleetBuilder.util


import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.codex2.CodexDialog
import fleetBuilder.persistence.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.VariantSerialization.saveVariantToJson
import fleetBuilder.util.ClipboardUtil.setClipboardText

object ClipboardFunctions {
    fun codexEntryToClipboard(codex: CodexDialog) {
        val param = MISC.getCodexEntryParam(codex)
        if (param == null) return

        when (param) {
            is ShipHullSpecAPI -> {
                val emptyVariant = Global.getSettings().createEmptyVariant(param.hullId, param)
                val json = saveVariantToJson(emptyVariant)
                setClipboardText(json.toString(4))
                MISC.showMessage("Copied codex variant to clipboard")
            }

            is FleetMemberAPI -> {
                val json = saveMemberToJson(param)
                setClipboardText(json.toString(4))
                MISC.showMessage("Copied codex member to clipboard")
            }
        }
    }
}