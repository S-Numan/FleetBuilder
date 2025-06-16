package fleetBuilder.ui

import MagicLib.*
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.combat.entities.Ship
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import fleetBuilder.ModSettings
import java.awt.Color

class AutofitCombatRefitAdder : BaseEveryFrameCombatPlugin() {
    companion object {
        var SHIP_PREVIEW_CLASS: Class<*>? = null
        var SHIPS_FIELD: String? = null
    }

    var hackButton: ButtonAPI? = null

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        val state = AppDriver.getInstance().currentState

        val newCoreUI = (state as? TitleScreenState)?.let {
            ReflectionUtils.invoke("getScreenPanel", it) as? UIPanelAPI
        } ?: return
        cacheShipPreviewClass(newCoreUI)

        if(!ModSettings.autofitMenuEnabled) return

        val delegateChild = newCoreUI.findChildWithMethod("dismiss") as? UIPanelAPI ?: return
        val oldCoreUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return
        val holographicBG = oldCoreUI.findChildWithMethod("forceFoldIn") ?: return

        val refitTab = holographicBG.let {
            ReflectionUtils.invoke("getCurr", it)
        } as? UIPanelAPI ?: return



        if(hackButton != null) return

        //HACK
        hackButton = refitTab.addButton(
            "",
            "TEMP_BUTTON",
            Color.BLACK,
            Color.BLACK,
            Alignment.MID,
            CutStyle.ALL,
            Font.ORBITRON_20,
            0f,
            0f
        )
        //hackButton!!.position.setLocation(-99999f, -99999f)
        hackButton!!.setShortcut(ModSettings.autofitMenuHotkey, true)
        hackButton!!.onClick {
            AutofitPanelCreator.toggleAutofitButton(refitTab, false)
        }
        /*
        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN) {
                if(event.eventValue == Keyboard.KEY_Z) {

                }
            }
        }*/


        //AutofitPanelCreator.toggleAutofitButton(refitTab, false)
    }

    private fun cacheShipPreviewClass(newCoreUI: UIPanelAPI) {
        if (SHIP_PREVIEW_CLASS != null) return

        val missionWidget = newCoreUI.findChildWithMethod("getMissionList") as? UIPanelAPI ?: return
        val holographicBG = missionWidget.getChildrenCopy()[1] // 2 of the same class in the tree here

        val missionDetail = holographicBG.let {
            ReflectionUtils.invoke("getCurr", it)
        } as? UIPanelAPI ?: return

        val missionShipPreview = missionDetail.getChildrenCopy().find {
            ReflectionUtils.hasConstructorOfParameters(it, missionDetail.javaClass)
        } as? UIPanelAPI ?: return

        val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return

        SHIP_PREVIEW_CLASS = shipPreview.javaClass
        val shipFields = ReflectionUtils.getFieldsOfType(shipPreview, Array<Ship>::class.java)
        SHIPS_FIELD = shipFields[0] // only one field should be Array<Ship>
    }
}