package fleetkit.features.autofit.listener

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetkit.core.config.FTSettings
import fleetkit.features.autofit.ui.AutofitPanelCreator
import fleetkit.otherMods.starficz.ReflectionUtilsSafe.safeInvoke
import fleetkit.otherMods.starficz.clearOnClick
import fleetkit.otherMods.starficz.findChildWithMethod
import fleetkit.otherMods.starficz.onClick
import org.lwjgl.input.Keyboard
import org.magiclib.util.reflection.boxed.BoxedRefitTab

internal class TitleAutofitAdder : BaseEveryFrameCombatPlugin() {

    var refitPanel: UIPanelAPI? = null

    override fun advance(amount: Float, events: MutableList<InputEventAPI>) {
        if (Global.getCurrentState() != GameState.TITLE) {
            refitPanel = null
            return
        }
        if (!FTSettings.autofitMenuEnabled) return

        val refitTab = BoxedRefitTab.get()
        if (refitTab == null) {
            refitPanel = null
            return
        }

        if (refitPanel === refitTab.refitPanel)
            return

        val bottomLeftPanel = refitTab.refitPanel.findChildWithMethod("instantiateForSimulation") as? UIPanelAPI ?: return

        val autofitButton = bottomLeftPanel.safeInvoke("getManageButton") as? ButtonAPI ?: return
        autofitButton.setShortcut(FTSettings.autofitMenuHotkey, false)

        autofitButton.clearOnClick()
        autofitButton.onClick {
            if (FTSettings.autofitMenuHotkey != Keyboard.KEY_NONE && Keyboard.isKeyDown(FTSettings.autofitMenuHotkey))
                AutofitPanelCreator.toggleAutofitButton(refitTab, false)
            else if (FTSettings.replaceVanillaAutofitButton) {
                AutofitPanelCreator.toggleAutofitButton(refitTab, false)
            } else {
                bottomLeftPanel.safeInvoke("actionPerformed", null, autofitButton)
            }
        }

        refitPanel = refitTab.refitPanel
    }
}