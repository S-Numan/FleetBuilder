package fleetBuilder.integration.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.ui.UITable
import fleetBuilder.ui.ModPickerFilterPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import starficz.ReflectionUtils.invoke
import starficz.findChildWithMethod
import starficz.getChildrenCopy

class CampaignModPickerFilter : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var filterPanel: ModPickerFilterPanel? = null
    var isPermMode: Boolean = false

    override fun advance(amount: Float) {
        if (!Global.getSector().isPaused) return

        val campaignState = Global.getSector().campaignUI
        if (campaignState.getActualCurrentTab() != CoreUITabId.REFIT) {
            if (filterPanel != null) {
                filterPanel = null
            }
            return
        }

        val coreUI = ReflectionMisc.getCoreUI() ?: return
        val modPicker = coreUI.findChildWithMethod("canInstallGivenMarket") as? UIPanelAPI
        if (modPicker == null) {
            filterPanel = null
            isPermMode = false
            return
        }

        val newPermMode = modPicker.invoke("isPermMode") as Boolean
        //On  panel appearing
        if (filterPanel == null) {
            filterPanel = ModPickerFilterPanel(200f, 30f, modPicker, modPicker)
        } else if (newPermMode != isPermMode) {
            filterPanel = ModPickerFilterPanel(200f, 30f, modPicker, modPicker)
            isPermMode = newPermMode
        }
    }
}