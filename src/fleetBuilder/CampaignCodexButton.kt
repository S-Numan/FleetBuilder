package fleetBuilder

import MagicLib.Font
import MagicLib.addButton
import MagicLib.width
import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.misc.MISC.getCodexDialog
import fleetBuilder.misc.MISC.getCodexEntryParam

class CampaignCodexButton: EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var buttonThing: UIPanelAPI? = null

    override fun advance(amount: Float) {
        if(!Global.getSector().isPaused) return
        if(Global.getSettings().isDevMode) {
            val codex = getCodexDialog()
            if(codex == null) return

            if(buttonThing == null) {
                buttonThing = codex.addButton(
                    "Atatata",
                    null,
                    Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(),
                    Alignment.MID,
                    CutStyle.ALL,
                    Font.ORBITRON_20,
                    25f, 25f
                ) as UIPanelAPI?
            }

            val param = getCodexEntryParam(codex)
            if(param == null) return

            val shipHull = param as? ShipHullSpecAPI
            val fleetMember = param as? FleetMemberAPI

            if(shipHull != null || fleetMember != null) {
                var exists = false
                val children = codex.childrenCopy as List<UIComponentAPI>
                children.forEach { child ->
                    if(child === buttonThing) {
                        exists = true
                    }
                }
                if(!exists) {

                }
            }
            /*
            if (shipHull != null) {
                val emptyVariant = Global.getSettings().createEmptyVariant(shipHull.hullId, shipHull)
                val json = saveVariantToJson(emptyVariant)

                showMessage("Added variant to fleet")
            }

            if (fleetMember != null) {
                val json = saveMemberToJson(fleetMember)

                showMessage("Added member to fleet")
            }*/

        }
    }
}