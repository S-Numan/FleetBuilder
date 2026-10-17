package fleetBuilder.features.filters.injection

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.features.filters.filterPanels.ModPickerFilterPanel
import fleetBuilder.otherMods.starficz.ReflectionUtilsSafe.safeInvoke
import fleetBuilder.otherMods.starficz.findChildWithMethod
import org.magiclib.util.reflection.boxed.BoxedRefitTab

internal class CampaignModPickerFilter : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    var filterPanel: ModPickerFilterPanel? = null
    var isPermMode: Boolean = false

    override fun advance(amount: Float) {
        if (!Global.getSector()!!.isPaused) return

        val refitTab = BoxedRefitTab.get()
        if (refitTab == null) {
            if (filterPanel != null) {
                filterPanel = null
            }
            return
        }

        val coreUI = refitTab.getCoreUI() as? UIPanelAPI ?: return
        val modPicker = coreUI.findChildWithMethod("canInstallGivenMarket") as? UIPanelAPI
        if (modPicker == null) {
            filterPanel = null
            isPermMode = false
            return
        }

        val newPermMode = modPicker.safeInvoke("isPermMode") as Boolean
        //On  panel appearing
        if (filterPanel == null) {
            filterPanel = ModPickerFilterPanel(200f, 30f, modPicker, modPicker)
        } else if (newPermMode != isPermMode) {
            filterPanel = ModPickerFilterPanel(200f, 30f, modPicker, modPicker)
            isPermMode = newPermMode
        }
    }
}