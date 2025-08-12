package fleetBuilder.integration.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.HullModSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.persistence.member.MemberSerialization
import fleetBuilder.persistence.member.MemberSerialization.extractMemberDataFromJson
import fleetBuilder.persistence.member.MemberSerialization.saveMemberToJson
import fleetBuilder.persistence.variant.VariantSerialization.extractVariantDataFromJson
import fleetBuilder.persistence.variant.VariantSerialization.saveVariantToJson
import fleetBuilder.util.DisplayMessage
import fleetBuilder.util.DisplayMessage.showMessage
import fleetBuilder.util.FBMisc.fleetPaste
import fleetBuilder.util.ReflectionMisc.getCodexDialog
import fleetBuilder.util.ReflectionMisc.getCodexEntryParam
import org.json.JSONObject
import org.lwjgl.input.Keyboard
import starficz.*

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
            field.get(codex)
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
                    24f, 24f,
                    Font.ORBITRON_20
                ) as ButtonAPI?

                addToFleetButton!!.addTooltip(TooltipMakerAPI.TooltipLocation.ABOVE, 400f) { tooltip ->
                    tooltip.addPara("Hold CTRL to add a blueprint if a blueprint is available\nHold ALT or SHIFT to multiply the value by 10", 0f)
                }

                addToFleetButton!!.onClick { ->
                    addCodexParamEntryToFleet(Global.getSector(), param!!)
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

    fun addCodexParamEntryToFleet(sector: SectorAPI, param: Any, ctrlCreatesBlueprints: Boolean = true) {
        val shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
        val alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)
        val ctrl = ctrlCreatesBlueprints && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

        val count = when {
            shift && alt -> 100
            shift || alt -> 10
            else -> 1
        }

        val cargo = Global.getSector().playerFleet.cargo

        var message: String? = null

        var parsedData: Any? = null

        when (param) {
            is CommoditySpecAPI -> {
                cargo.addCommodity(param.id, count.toFloat())
                message = "Added $count '${param.name}' to cargo"
            }

            is SpecialItemSpecAPI -> {
                cargo.addSpecial(SpecialItemData(param.id, null), count.toFloat())
                message = "Added $count '${param.name}' to cargo"
            }

            is WeaponSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("weapon_bp", param.weaponId), count.toFloat())
                    message = "Added $count '${param.weaponName}' blueprint to your cargo"
                } else {
                    cargo.addWeapons(param.weaponId, count)
                    message = "Added $count '${param.weaponName}' to cargo"
                }
            }

            is FighterWingSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("fighter_bp", param.id), count.toFloat())
                    message = "Added $count '${param.wingName}' blueprint to your cargo"
                } else {
                    cargo.addFighters(param.id, count)
                    message = "Added $count '${param.wingName}' to cargo"
                }
            }

            is HullModSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("modspec", param.id), count.toFloat())
                    message = "Added $count '${param.displayName}' blueprint to your cargo"
                } else {
                    Global.getSector().playerFaction.addKnownHullMod(param.id)
                    message = "Added '${param.displayName}' your faction's known hullmods"
                }
            }

            is ShipHullSpecAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("ship_bp", param.hullId), count.toFloat())
                    message = "Added $count '${param.hullName}' blueprint to your cargo"
                } else {
                    val emptyVariant =
                        Global.getSettings().createEmptyVariant(param.hullId, param)
                    parsedData = extractVariantDataFromJson(saveVariantToJson(emptyVariant))
                }
            }

            is FleetMemberAPI -> {
                if (ctrl) {
                    cargo.addSpecial(SpecialItemData("ship_bp", param.hullId), count.toFloat())
                    message = "Added $count '${param.hullSpec.hullName}' blueprint to your cargo"
                } else {
                    parsedData = extractMemberDataFromJson(saveMemberToJson(param))
                }
            }
        }

        if (parsedData != null) {

            repeat(count) {
                fleetPaste(sector, parsedData)
            }
        }

        if (!message.isNullOrEmpty())
            showMessage(message)
    }
}