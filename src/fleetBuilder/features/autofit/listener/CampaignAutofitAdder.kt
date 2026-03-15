package fleetBuilder.features.autofit.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.ModSettings
import fleetBuilder.features.autofit.ui.AutofitPanelCreator
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.safeInvoke
import org.lwjgl.input.Keyboard
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.onClick

internal class CampaignAutofitAdder : EveryFrameScript {

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var refitTab: UIPanelAPI? = null
    override fun advance(amount: Float) {
        val sector = Global.getSector() ?: return
        if (!sector.isPaused) {
            refitTab = null
            return
        }

        val ui = sector.campaignUI ?: return

        if (ui.getActualCurrentTab() != CoreUITabId.REFIT) {
            refitTab = null
            return
        }

        val refitTab = ReflectionMisc.getRefitTab() ?: return
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
}