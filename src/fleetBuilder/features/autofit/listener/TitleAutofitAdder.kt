package fleetBuilder.features.autofit.listener

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.FBSettings
import fleetBuilder.features.autofit.ui.AutofitPanelCreator
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.safeInvoke
import org.lwjgl.input.Keyboard

internal class TitleAutofitAdder : BaseEveryFrameCombatPlugin() {
    companion object {
        var refitTab: UIPanelAPI? = null
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>) {
        if (Global.getCurrentState() != GameState.TITLE)
            return

        if (!FBSettings.autofitMenuEnabled) return

        val screenPanel = ReflectionMisc.getScreenPanel() ?: return
        val delegateChild = screenPanel.findChildWithMethod("dismiss") as? UIPanelAPI ?: return
        val oldCoreUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return
        val holographicBG = oldCoreUI.findChildWithMethod("forceFoldIn") ?: return

        val refitTab = holographicBG.safeInvoke("getCurr") as? UIPanelAPI ?: return

        if (Companion.refitTab === refitTab)
            return

        val refitPanel = refitTab.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI ?: return
        val bottomLeftPanel = refitPanel.findChildWithMethod("instantiateForSimulation") as? UIPanelAPI ?: return

        val autofitButton = bottomLeftPanel.safeInvoke("getManageButton") as? ButtonAPI ?: return
        autofitButton.setShortcut(FBSettings.autofitMenuHotkey, false)

        autofitButton.onClick {
            if (FBSettings.autofitMenuHotkey != Keyboard.KEY_NONE && Keyboard.isKeyDown(FBSettings.autofitMenuHotkey))
                AutofitPanelCreator.toggleAutofitButton(refitTab, true)
            else if (FBSettings.replaceVanillaAutofitButton) {
                AutofitPanelCreator.toggleAutofitButton(refitTab, true)
            } else {
                bottomLeftPanel.safeInvoke("actionPerformed", null, autofitButton)
            }
        }

        Companion.refitTab = refitTab
    }
}