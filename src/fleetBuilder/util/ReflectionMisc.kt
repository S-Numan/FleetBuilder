package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.econ.Submarket
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.campaign.ui.UITable
import com.fs.starfarer.codex2.CodexDetailPanel
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.combat.CombatState
import com.fs.starfarer.coreui.CaptainPickerDialog
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import starficz.ReflectionUtils.get
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke

object ReflectionMisc {
    fun getCoreUI(topDialog: Boolean = true): UIPanelAPI? {
        val state = AppDriver.getInstance().currentState
        if (state is CampaignState) {

            return (state.invoke("getEncounterDialog")?.let { dialog ->
                if (topDialog && Global.getSector().campaignUI.getActualCurrentTab() == null) {//In encounter dialog, but not looking at any tab.
                    //If you add a dialog to CoreUI and move it to the top, it will usually show in the top. Excluding this situation. Thus, in this situation we just get the screen panel instead. This can be disabled via the topDialog boolean.
                    state.invoke("getScreenPanel") as? UIPanelAPI
                } else {
                    dialog.invoke("getCoreUI") as? UIPanelAPI
                }
            } ?: state.invoke("getCore") as? UIPanelAPI)

        } else if (state is TitleScreenState || state is CombatState) {
            return state.invoke("getScreenPanel") as? UIPanelAPI
        }
        return null
    }

    fun getBorderContainer(): UIPanelAPI? {
        return getCoreUI()?.findChildWithMethod("setBorderInsetLeft") as? UIPanelAPI
    }

    fun getRefitTab(): UIPanelAPI? {
        return getBorderContainer()?.findChildWithMethod("goBackToParentIfNeeded") as? UIPanelAPI
    }

    fun getRefitPanel(): UIPanelAPI? {
        return getRefitTab()?.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI
    }

    fun getCurrentVariantInRefitTab(): ShipVariantAPI? {
        val shipDisplay = getRefitPanel()?.invoke("getShipDisplay") as? UIPanelAPI ?: return null
        return shipDisplay.invoke("getCurrentVariant") as? ShipVariantAPI
    }

    fun getFleetTab(): UIPanelAPI? {
        val campaignState = Global.getSector().campaignUI
        if (campaignState?.getActualCurrentTab() != CoreUITabId.FLEET)
            return null
        else
            return getCoreUI()?.invoke("getCurrentTab") as? UIPanelAPI
    }

    fun getFleetPanel(): UIPanelAPI? {
        return getFleetTab()?.findChildWithMethod("getOther") as? UIPanelAPI
    }

    fun getFleetSidePanel(): UIPanelAPI? {
        val children = getFleetTab()?.getChildrenCopy()
        return children?.find { it.getFieldsMatching(type = UITable::class.java).isNotEmpty() } as? UIPanelAPI
    }

    fun getMemberUIHoveredInFleetTabLowerPanel(): UIPanelAPI? {
        val fleetTab = getFleetTab() ?: return null
        val mouseOverMember = fleetTab.invoke("getMousedOverFleetMember") as? FleetMemberAPI ?: return null

        val fleetPanel = getFleetPanel() ?: return null
        val list = fleetPanel.invoke("getList") ?: return null
        val items = list.invoke("getItems") as? List<Any?>
            ?: return null//Core UI box that contains everything related to the fleet member, including the ship, officer, cr, etc. There is one for each member in your fleet.

        // Find UI element of which the mouse is hovering over
        items.forEach { item ->
            if (item == null) return@forEach

            //Get all children for this item
            val children = item.invoke("getChildrenCopy") as? List<Any?> ?: return@forEach

            //Find the UI child with a portrait button
            val foundUI = children.firstOrNull { child ->
                child != null && child.getMethodsMatching(name = "getPortraitButton").isNotEmpty()
            } ?: return@forEach

            //Get FleetMember
            val fields = foundUI.getFieldsMatching(type = FleetMember::class.java)
            if (fields.isEmpty()) return@forEach

            //Return if this item's fleet member is not the one we are hovering over
            val member = fields[0].get(foundUI) as? FleetMemberAPI
            if (member?.id != mouseOverMember.id) return@forEach

            //If we've got here, this is the UI item the mouse is hovering over.
            return foundUI as UIPanelAPI?
        }

        return null
    }

    fun isCodexOpen(): Boolean {
        if (Global.getSettings().isShowingCodex) return true

        if (Global.getCurrentState() == GameState.CAMPAIGN && Global.getSector().campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            val coreUI = getCoreUI()
            if (coreUI?.findChildWithMethodReversed("getCurrentSnapshot") is CodexDialog) {
                return true
            }
        }
        return false
    }

    fun getCodexDialog(): CodexDialog? {
        val state = AppDriver.getInstance().currentState

        if (Global.getSettings().isShowingCodex) { //isShowingCodex does not work in all cases as of 0.98
            if (Global.getCurrentState() == GameState.COMBAT) {
                //Combat F2 with ship selected, simulator ship F2.
                if (state.getMethodsMatching("getRibbon").isNotEmpty()) {
                    val ribbon = state.invoke("getRibbon") as? UIPanelAPI?
                    val temp = ribbon?.invoke("getParent") as? UIPanelAPI?
                    val codex = temp?.findChildWithMethod("getCurrentSnapshot") as? CodexDialog?
                    if (codex != null) return codex
                }
                //Note that the codex that opens from clicking the combat "More Info" question mark button appears in the below and not the above
            }

            //F2 press, and in some other places
            val codexOverlayPanel = state.invoke("getOverlayPanelForCodex") as? UIPanelAPI?
            return codexOverlayPanel?.findChildWithMethod("getCurrentSnapshot") as? CodexDialog?
        }

        //settingsCodex is false despite codex being open
        if (Global.getCurrentState() == GameState.CAMPAIGN && Global.getSector().campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            //F2 while hovering over ship or ship question mark press in the fleet screen. NOT hover over question mark and press F2, that is handled differently for some reason.
            val coreUI = getCoreUI()
            return coreUI?.findChildWithMethodReversed("getCurrentSnapshot") as? CodexDialog?

        }

        return null
    }

    fun getCodexEntryParam(codex: CodexDialog): Any? {
        val codexDetailPanel = codex.get(type = CodexDetailPanel::class.java) ?: return null
        val codexEntry = codexDetailPanel.get(name = "plugin") ?: return null

        return codexEntry.invoke("getParam")
    }

    fun getCaptainPickerDialog(): CaptainPickerDialog? {
        val core = getCoreUI() ?: return null
        val children = core.invoke("getChildrenNonCopy") as? MutableList<*> ?: return null
        return children.firstOrNull { it is CaptainPickerDialog } as? CaptainPickerDialog
    }

    fun getViewedFleetInFleetPanel(
    ): FleetDataAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            val fleetPanel = getFleetPanel() ?: return null

            return fleetPanel.invoke("getFleetData") as? FleetDataAPI
        }
        return null
    }

    fun getSelectedSubmarketInFleetTab(
    ): SubmarketAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET && campaignUI.isShowingDialog) {
            val dialog = campaignUI.currentInteractionDialog ?: return null
            dialog.interactionTarget?.market ?: return null

            val fleetTab = getFleetTab() ?: return null

            return fleetTab
                .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                .getOrNull(0)
                ?.get(fleetTab) as? SubmarketAPI
        }
        return null
    }

    private var postUpdateFleetPanelCallbacks = mutableListOf<() -> Unit>()
    fun addPostUpdateFleetPanelCallback(callback: () -> Unit) {
        if (postUpdateFleetPanelCallbacks.contains(callback)) return

        postUpdateFleetPanelCallbacks.add(callback)
    }

    fun removePostUpdateFleetPanelCallback(callback: () -> Unit) {
        postUpdateFleetPanelCallbacks.remove(callback)
    }

    fun updateFleetPanelContents() {
        if (Global.getSector().campaignUI.getActualCurrentTab() != CoreUITabId.FLEET) return

        var fleetPanel: UIPanelAPI? = null
        try {
            val fleetTab = getFleetTab()
            if (fleetTab != null)
                fleetPanel = fleetTab.invoke("getFleetPanel") as? UIPanelAPI
        } catch (_: Exception) {
        }
        fleetPanel?.invoke("updateListContents")

        postUpdateFleetPanelCallbacks.forEach { it.invoke() }
    }
}