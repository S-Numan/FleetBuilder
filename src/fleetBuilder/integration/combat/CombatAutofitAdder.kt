package fleetBuilder.integration.combat

import MagicLib.Font
import MagicLib.addButton
import MagicLib.findChildWithMethod
import MagicLib.getChildrenCopy
import MagicLib.onClick
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.combat.entities.Ship
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import fleetBuilder.config.ModSettings
import fleetBuilder.ui.autofit.AutofitPanelCreator
import starficz.ReflectionUtils.invoke
import java.awt.Color
import MagicLib.ReflectionUtils
import com.fs.starfarer.api.input.InputEventType
import fleetBuilder.config.ModSettings.autofitMenuHotkey
import fleetBuilder.util.ClipboardFunctions.codexEntryToClipboard
import fleetBuilder.util.MISC.getCodexDialog
import fleetBuilder.util.MISC.showError
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.getFieldsMatching

class CombatAutofitAdder : BaseEveryFrameCombatPlugin() {
    companion object {
        var SHIP_PREVIEW_CLASS: Class<*>? = null
        var SHIPS_FIELD: String? = null
    }

    //var hackButton: ButtonAPI? = null
    var keyDown = false

    override fun advance(amount: Float, events: MutableList<InputEventAPI>) {
        val state = AppDriver.getInstance().currentState

        val newCoreUI = (state as? TitleScreenState)?.let {
            it.invoke("getScreenPanel") as? UIPanelAPI
        } ?: return
        cacheShipPreviewClass(newCoreUI)

        if(!ModSettings.autofitMenuEnabled) return

        val delegateChild = newCoreUI.findChildWithMethod("dismiss") as? UIPanelAPI ?: return
        val oldCoreUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return
        val holographicBG = oldCoreUI.findChildWithMethod("forceFoldIn") ?: return

        val refitTab = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return



        if (Keyboard.isKeyDown(autofitMenuHotkey)) {
            if(!keyDown) {
                AutofitPanelCreator.toggleAutofitButton(refitTab, false)
                keyDown = true
            }
        } else if(keyDown) {
            keyDown = false
        }



        /*
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
        }*/


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

        val missionDetail = holographicBG.invoke("getCurr") as? UIPanelAPI ?: return

        val missionShipPreview = missionDetail.getChildrenCopy().find {
            ReflectionUtils.hasConstructorOfParameters(it, missionDetail.javaClass)
        } as? UIPanelAPI ?: return

        val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return

        SHIP_PREVIEW_CLASS = shipPreview.javaClass
        val shipFields = shipPreview.getFieldsMatching(type = Array<Ship>::class.java)
        SHIPS_FIELD = shipFields[0].name // only one field should be Array<Ship>
    }
}