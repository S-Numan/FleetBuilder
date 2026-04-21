package fleetBuilder.features.autofit.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.FBSettings
import fleetBuilder.features.autofit.ui.AutofitPanelCreator
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.kotlin.getActualCurrentTab
import fleetBuilder.util.api.kotlin.safeInvoke
import org.lwjgl.input.Keyboard

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

        this.refitTab = refitTab
    }
}