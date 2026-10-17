package fleetBuilder.features.autofit.listener

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.config.FBSettings
import fleetBuilder.features.autofit.ui.AutofitPanelCreator
import fleetBuilder.otherMods.starficz.ReflectionUtilsSafe.safeInvoke
import fleetBuilder.otherMods.starficz.clearOnClick
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.onClick
import org.lwjgl.input.Keyboard
import org.magiclib.util.reflection.boxed.BoxedRefitTab

internal class TitleAutofitAdder : BaseEveryFrameCombatPlugin() {

    var refitPanel: UIPanelAPI? = null

    override fun advance(amount: Float, events: MutableList<InputEventAPI>) {
        if (Global.getCurrentState() != GameState.TITLE) {
            refitPanel = null
            return
        }
        if (!FBSettings.autofitMenuEnabled) return

        val refitTab = BoxedRefitTab.get()
        if (refitTab == null) {
            refitPanel = null
            return
        }

        if (refitPanel === refitTab.refitPanel)
            return

        val bottomLeftPanel = refitTab.refitPanel.findChildWithMethod("instantiateForSimulation") as? UIPanelAPI ?: return

        val autofitButton = bottomLeftPanel.safeInvoke("getManageButton") as? ButtonAPI ?: return
        autofitButton.setShortcut(FBSettings.autofitMenuHotkey, false)

        autofitButton.clearOnClick()
        autofitButton.onClick {
            if (FBSettings.autofitMenuHotkey != Keyboard.KEY_NONE && Keyboard.isKeyDown(FBSettings.autofitMenuHotkey))
                AutofitPanelCreator.toggleAutofitButton(refitTab, false)
            else if (FBSettings.replaceVanillaAutofitButton) {
                AutofitPanelCreator.toggleAutofitButton(refitTab, false)
            } else {
                bottomLeftPanel.safeInvoke("actionPerformed", null, autofitButton)
            }
        }

        refitPanel = refitTab.refitPanel
    }
}