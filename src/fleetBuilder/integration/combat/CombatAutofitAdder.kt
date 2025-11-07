package fleetBuilder.integration.combat


import MagicLib.ReflectionUtilsExtra
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.combat.entities.Ship
import fleetBuilder.config.ModSettings
import fleetBuilder.ui.autofit.AutofitPanelCreator
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.safeInvoke
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.getFieldsMatching
import starficz.findChildWithMethod
import starficz.getChildrenCopy
import starficz.onClick

internal class CombatAutofitAdder : BaseEveryFrameCombatPlugin() {
    companion object {
        var SHIP_PREVIEW_CLASS: Class<*>? = null
        var SHIPS_FIELD: String? = null
    }

    var refitTab: UIPanelAPI? = null

    override fun advance(amount: Float, events: MutableList<InputEventAPI>) {
        if (Global.getCurrentState() != GameState.TITLE)
            return

        val newCoreUI = ReflectionMisc.getCoreUI() ?: return
        cacheShipPreviewClass(newCoreUI)

        if (!ModSettings.autofitMenuEnabled) return

        val delegateChild = newCoreUI.findChildWithMethod("dismiss") as? UIPanelAPI ?: return
        val oldCoreUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return
        val holographicBG = oldCoreUI.findChildWithMethod("forceFoldIn") ?: return

        val refitTab = holographicBG.safeInvoke("getCurr") as? UIPanelAPI ?: return

        if (this.refitTab === refitTab)
            return

        val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI ?: return
        val bottomLeftPanel = refitPanel.findChildWithMethod("instantiateForSimulation") as? UIPanelAPI ?: return
        val autofitButton = bottomLeftPanel.safeInvoke("getManageButton") as? ButtonAPI ?: return
        autofitButton.setShortcut(ModSettings.autofitMenuHotkey, false)

        autofitButton.onClick {
            if (ModSettings.autofitMenuHotkey != Keyboard.KEY_NONE && Keyboard.isKeyDown(ModSettings.autofitMenuHotkey))
                AutofitPanelCreator.toggleAutofitButton(refitTab, true)
            else if (ModSettings.replaceVanillaAutofitButton) {
                AutofitPanelCreator.toggleAutofitButton(refitTab, true)
            } else {
                bottomLeftPanel.safeInvoke("actionPerformed", null, autofitButton)
            }
        }

        this.refitTab = refitTab
    }

    private fun cacheShipPreviewClass(newCoreUI: UIPanelAPI) {
        if (SHIP_PREVIEW_CLASS != null) return

        val missionWidget = newCoreUI.findChildWithMethod("getMissionList") as? UIPanelAPI ?: return
        val holographicBG = missionWidget.getChildrenCopy()[1] // 2 of the same class in the tree here

        val missionDetail = holographicBG.safeInvoke("getCurr") as? UIPanelAPI ?: return

        val missionShipPreview = missionDetail.getChildrenCopy().find {
            //it.javaClass.getConstructorsMatching(numOfParams = 1, parameterTypes = arrayOf(missionDetail.javaClass)).isNotEmpty()// File access/reflection error
            ReflectionUtilsExtra.hasConstructorOfParameters(it, missionDetail.javaClass)
        } as? UIPanelAPI ?: return

        val shipPreview = missionShipPreview.findChildWithMethod("isSchematicMode") ?: return

        SHIP_PREVIEW_CLASS = shipPreview.javaClass
        val shipFields = shipPreview.getFieldsMatching(type = Array<Ship>::class.java)
        SHIPS_FIELD = shipFields[0].name // only one field should be Array<Ship>
    }
}