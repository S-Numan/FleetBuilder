package fleetkit.features.autofit.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetkit.core.config.FTSettings
import fleetkit.features.autofit.ui.AutofitPanelCreator
import fleetkit.otherMods.starficz.ReflectionUtilsSafe.safeInvoke
import fleetkit.otherMods.starficz.findChildWithMethod
import fleetkit.otherMods.starficz.onClick
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
        autofitButton.setShortcut(FTSettings.autofitMenuHotkey, false)

        autofitButton.onClick {
            if (FTSettings.autofitMenuHotkey != Keyboard.KEY_NONE && Keyboard.isKeyDown(FTSettings.autofitMenuHotkey))
                AutofitPanelCreator.toggleAutofitButton(refitTab, true)
            else if (FTSettings.replaceVanillaAutofitButton) {
                AutofitPanelCreator.toggleAutofitButton(refitTab, true)
            } else {
                bottomLeftPanel.safeInvoke("actionPerformed", null, autofitButton)
            }
        }

        this.refitPanel = refitTab.refitPanel
    }
}