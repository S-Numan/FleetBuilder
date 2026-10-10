package fleetBuilder.features.autofit.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.config.FBSettings
import fleetBuilder.features.autofit.ui.AutofitPanelCreator
import fleetBuilder.otherMods.starficz.ReflectionUtilsSafe.safeInvoke
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.onClick
import org.lwjgl.input.Keyboard
import org.magiclib.util.reflection.boxed.BoxedRefitTab

internal class CampaignAutofitAdder : EveryFrameScript {

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var refitPanel: UIPanelAPI? = null
    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) {
            refitPanel = null
            return
        }

        val refitTab = BoxedRefitTab.get()
        if (refitTab == null) {
            refitPanel = null
            return
        }
        if (this.refitPanel === refitTab.refitPanel)
            return

        val bottomLeftPanel = refitTab.refitPanel.findChildWithMethod("instantiateForSimulation") as? UIPanelAPI ?: return
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

        this.refitPanel = refitTab.refitPanel
    }
}