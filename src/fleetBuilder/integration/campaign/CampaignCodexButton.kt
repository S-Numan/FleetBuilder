package fleetBuilder.integration.campaign

import MagicLib.*
import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.FBMisc
import fleetBuilder.util.ReflectionMisc.getCodexDialog
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import org.lwjgl.input.Keyboard

class CampaignCodexButton : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var addToFleetButton: ButtonAPI? = null
    var param: Any? = null

    override fun advance(amount: Float) {
        if (!Global.getSector().isPaused) return
        if (!Global.getSettings().isDevMode) return

        val codex = getCodexDialog()
        if (codex == null) {
            if (addToFleetButton != null)
                addToFleetButton = null
            return
        }
        /*val uiFields = codex.getFieldsMatching(fieldAssignableTo = UIPanelAPI::class.java)
        val tempArray = uiFields.map { field ->
            codex.get(field.name)
        }
        (tempArray[2] as TextFieldAPI)//Text input field for searching
        val navContainer = (tempArray[3] as UIPanelAPI)//Bottom left: Left, Right, Up, Random, icons UI container.*/

        val newParam = getCodexEntryParam(codex)
        if (param !== newParam) {
            param = newParam

            codex.removeComponent(addToFleetButton)
            addToFleetButton = null
        }

        if (//Can this param be added to the fleet
            param is CommoditySpecAPI ||
            param is SpecialItemSpecAPI ||
            param is WeaponSpecAPI ||
            param is FighterWingSpecAPI ||
            param is ShipHullSpecAPI ||
            param is FleetMemberAPI ||
            param is HullModSpecAPI
        ) {
            val pad = 18f

            var exists = false
            val children = codex.getChildrenCopy() as List<UIComponentAPI>
            children.forEach { child ->
                if (child === addToFleetButton) {
                    exists = true
                }
            }
            if (!exists) {
                addToFleetButton = codex.addButton(
                    "",
                    null,
                    Misc.getButtonTextColor(),
                    Misc.getDarkPlayerColor(),
                    Alignment.MID,
                    CutStyle.NONE,
                    Font.ORBITRON_20,
                    24f, 24f
                ) as ButtonAPI?

                addToFleetButton!!.onClick { ->
                    FBMisc.addCodexParamEntryToFleet(Global.getSector(), param!!)
                }
            }

            if (addToFleetButton == null) {
                DisplayMessage.showError("addToFleetButton was null, when it shouldn't be.")
                return
            }

            val shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
            val alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)
            val ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

            val count = when {
                shift && alt -> 100
                shift || alt -> 10
                else -> 1
            }


            val entry: String

            if (ctrl && param !is CommoditySpecAPI && param !is SpecialItemSpecAPI)
                entry = "Add blueprint"
            else if (param is HullModSpecAPI) {
                if (Global.getSector().playerFaction.knowsHullMod((param as HullModSpecAPI).id))
                    entry = "Already known"
                else
                    entry = "Add to faction"
            } else
                entry = "Add $count to fleet"

            //val width = Global.getSettings().computeStringWidth(entry, Fonts.ORBITRON_20AA) // Perhaps it's better that the button's width itself does not change, rather only the text within
            addToFleetButton!!.width = 154f
            addToFleetButton!!.text = entry
            addToFleetButton!!.xAlignOffset = codex.width - addToFleetButton!!.width - pad
            addToFleetButton!!.yAlignOffset = -codex.height + addToFleetButton!!.height + pad


        } else {
            codex.removeComponent(addToFleetButton)
            addToFleetButton = null
        }
    }
}