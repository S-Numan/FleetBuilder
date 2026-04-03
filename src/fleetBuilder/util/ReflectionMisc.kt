package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.campaign.econ.SubmarketAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.LabelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.CampaignState
import com.fs.starfarer.campaign.econ.Submarket
import com.fs.starfarer.campaign.fleet.FleetMember
import com.fs.starfarer.campaign.ui.UITable
import com.fs.starfarer.codex2.CodexDetailPanel
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.coreui.CaptainPickerDialog
import com.fs.starfarer.coreui.refit.ModWidget
import com.fs.state.AppDriver
import fleetBuilder.otherMods.starficz.ReflectionUtils.get
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.kotlin.getActualCurrentTab
import fleetBuilder.util.kotlin.safeGet
import fleetBuilder.util.kotlin.safeInvoke

object ReflectionMisc {

    fun getScreenPanel(): UIPanelAPI? {
        val state = AppDriver.getInstance().currentState
        return state.safeInvoke("getScreenPanel") as? UIPanelAPI
    }

    // Tip: Core UI is a child of the screen panel
    fun getCoreUI(): CoreUIAPI? {
        val state = AppDriver.getInstance().currentState
        if (state is CampaignState) {
            return (state.safeInvoke("getEncounterDialog")?.let { dialog ->
                dialog.safeInvoke("getCoreUI") as? CoreUIAPI
            } ?: Global.getSector().campaignUI?.safeGet("core") as? CoreUIAPI
            ?: state.safeInvoke("getCore") as? CoreUIAPI)
        }// else if (state is TitleScreenState || state is CombatState) {
        //  return null
        //  }
        return null
    }

    fun getBorderContainer(): UIPanelAPI? {
        return (getCoreUI() as? UIPanelAPI)?.findChildWithMethod("setBorderInsetLeft") as? UIPanelAPI
    }

    fun getRefitTab(): UIPanelAPI? {
        if (Global.getCurrentState() == GameState.CAMPAIGN) {
            return getBorderContainer()?.findChildWithMethod("goBackToParentIfNeeded") as? UIPanelAPI
        } else {
            val delegateChild = getScreenPanel()?.findChildWithMethod("dismiss") as? UIPanelAPI ?: return null
            val oldCoreUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return null
            val holographicBG = oldCoreUI.findChildWithMethod("forceFoldIn") ?: return null

            return holographicBG.safeInvoke("getCurr") as? UIPanelAPI ?: return null
        }
    }

    fun getRefitPanel(): UIPanelAPI? {
        return getRefitTab()?.findChildWithMethod("syncWithCurrentVariant") as? UIPanelAPI
    }

    fun getRefitPanelModWidget(refitPanel: UIPanelAPI): ModWidget? {
        val children = refitPanel.getChildrenCopy()
        var desiredChild: UIPanelAPI? = null
        children.forEach { child ->
            var yup = false

            val panel = child as? UIPanelAPI
            val childsChildren = panel?.getChildrenCopy()
            childsChildren?.forEach { childChildChild ->
                if (childChildChild.getMethodsMatching("removeNotApplicableMods").isNotEmpty()) {
                    yup = true
                    return@forEach
                }
            }
            if (yup) {
                desiredChild = child as? UIPanelAPI
                return@forEach
            }
        }

        return desiredChild?.findChildWithMethod("removeNotApplicableMods") as? ModWidget
    }

    fun getShipDisplayInRefitTab(): UIPanelAPI? {
        return getRefitPanel()?.safeInvoke("getShipDisplay") as? UIPanelAPI
    }

    fun getCurrentVariantInRefitTab(): ShipVariantAPI? {
        return getShipDisplayInRefitTab()?.safeInvoke("getCurrentVariant") as? ShipVariantAPI
    }

    fun getCurrentMemberInRefitTab(): FleetMemberAPI? {
        return getRefitPanel()?.safeInvoke("getMember") as? FleetMemberAPI
    }

    fun getCurrentTab(): UIPanelAPI? {
        return getCoreUI()?.safeInvoke("getCurrentTab") as? UIPanelAPI
    }

    fun getFleetTab(): UIPanelAPI? {
        val campaignState = Global.getSector().campaignUI
        return if (campaignState?.getActualCurrentTab() != CoreUITabId.FLEET)
            null
        else
            getCurrentTab()
    }

    fun getCargoTab(): UIPanelAPI? {
        val campaignState = Global.getSector().campaignUI
        return if (campaignState?.getActualCurrentTab() != CoreUITabId.CARGO)
            null
        else
            getCurrentTab()
    }

    fun getCargoPanel(): UIPanelAPI? {
        return getCargoTab()?.findChildWithMethod("shouldShowLogisticsOnSwitch") as? UIPanelAPI
        //val transferHandler = cargoTabChild?.invoke("getTransferHandler")// Howto get Cargo drawn when picked up with the mouse

        //Alternative method
        //val border = ReflectionMisc.getBorderContainer()
        //val cargoTab = border?.findChildWithMethod("shouldShowLogisticsOnSwitch") as? UIPanelAPI ?: return null
    }

    /*fun getSelectedSubmarketInCargoTab(
    ): SubmarketAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.CARGO && campaignUI.isShowingDialog) {
            val dialog = campaignUI.currentInteractionDialog ?: return null
            dialog.interactionTarget?.market ?: return null

            val cargoTab = getCargoTab() ?: return null

            return cargoTab
                .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                .getOrNull(0)
                ?.get(cargoTab) as? SubmarketAPI
        }
        return null
    }*/

    fun getFleetPanel(): UIPanelAPI? {
        return getFleetTab()?.findChildWithMethod("getOther") as? UIPanelAPI
    }

    fun getFleetSidePanel(): UIPanelAPI? {
        val children = getFleetTab()?.getChildrenCopy()
        return children?.find { it.getFieldsMatching(type = UITable::class.java).isNotEmpty() } as? UIPanelAPI
    }

    fun getMemberUIHoveredInFleetTabLowerPanel(): UIPanelAPI? {
        val fleetTab = getFleetTab() ?: return null
        val mouseOverMember = fleetTab.safeInvoke("getMousedOverFleetMember") as? FleetMemberAPI ?: return null

        val fleetPanel = getFleetPanel() ?: return null
        val list = fleetPanel.safeInvoke("getList") ?: return null
        val items = list.safeInvoke("getItems") as? List<Any?>
            ?: return null//Core UI box that contains everything related to the fleet member, including the ship, officer, cr, etc. There is one for each member in your fleet.

        // Find UI element of which the mouse is hovering over
        items.forEach { item ->
            if (item == null) return@forEach

            //Get all children for this item
            val children = item.safeInvoke("getChildrenCopy") as? List<Any?> ?: return@forEach

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

        val gameState = Global.getCurrentState()

        // F2 while hovering over ship in the fleet screen. Clicking the question mark in the fleet screen. Does not include hovering over the question mark and pressing F2
        if (gameState == GameState.CAMPAIGN && Global.getSector().campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            val coreUI = getCoreUI() as? UIPanelAPI ?: return false
            if (coreUI.getChildrenCopy().any { it is CodexDialog })
                return true
        }

        // Check for the codex that opens when clicking a ship in the title-screen missions
        if (gameState == GameState.TITLE) {
            if (getScreenPanel()?.getChildrenCopy()?.any { it is CodexDialog } == true)
                return true
        }
        return false
    }

    fun getCodexDialog(): CodexDialog? {
        val gameState = Global.getCurrentState()

        if (Global.getSettings().isShowingCodex) { //isShowingCodex does not work in all cases as of 0.98
            val appState = AppDriver.getInstance().currentState

            if (gameState == GameState.COMBAT) {
                //Combat F2 with ship selected, simulator ship F2.
                if (appState.getMethodsMatching("getRibbon").isNotEmpty()) {
                    val ribbon = appState.safeInvoke("getRibbon") as? UIPanelAPI?
                    val temp = ribbon?.safeInvoke("getParent") as? UIPanelAPI?
                    val codex = temp?.getChildrenCopy()?.find { it is CodexDialog } as? CodexDialog
                    if (codex != null) return codex
                }
                //Note that the codex that opens from clicking the combat "More Info" question mark button appears in the below and not the above
            }

            //F2 press, and in some other places
            val codexOverlayPanel = appState.safeInvoke("getOverlayPanelForCodex") as? UIPanelAPI?
            val codex = codexOverlayPanel?.getChildrenCopy()?.find { it is CodexDialog } as? CodexDialog
            if (codex != null)
                return codex

            //Codex button in main menu and ESC menu
            return getScreenPanel()?.getChildrenCopy()?.find { it is CodexDialog } as? CodexDialog
        }

        if (gameState == GameState.CAMPAIGN && Global.getSector().campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            //F2 while hovering over ship in the fleet screen. Clicking the question mark in the fleet screen. Does not include hovering over the question mark and pressing F2, that is handled differently for some reason.
            val coreUI = getCoreUI() as? UIPanelAPI

            val codex = coreUI?.getChildrenCopy()?.find { it is CodexDialog } as? CodexDialog
            if (codex != null) return codex
        }

        //Check for the codex that opens when clicking a ship in the title-screen missions.
        if (gameState == GameState.TITLE) {
            return getScreenPanel()?.getChildrenCopy()?.find { it is CodexDialog } as? CodexDialog
        }

        return null
    }

    fun getCodexDetailPanel(codex: CodexDialog): UIPanelAPI? {
        return codex.get(type = CodexDetailPanel::class.java) as? UIPanelAPI
    }

    fun getCodexEntryParam(codex: CodexDialog): Any? {
        val codexDetailPanel = getCodexDetailPanel(codex) ?: return null
        val codexEntry = codexDetailPanel.get(name = "plugin") ?: return null

        return codexEntry.safeInvoke("getParam")
    }

    fun getCodexDetailLabel(codex: CodexDialog): LabelAPI? {
        return getCodexDetailPanel(codex)?.getChildrenCopy()?.filterIsInstance<LabelAPI>()?.firstOrNull()
    }

    fun getBelowTitleDeeperPanel(codex: CodexDialog): UIPanelAPI? {
        val belowTitleBarPanel = getCodexDetailPanel(codex)?.findChildWithMethod("addToOverlay") as? UIPanelAPI
        return belowTitleBarPanel?.getChildrenCopy()?.find { (it as? UIPanelAPI)?.getChildrenCopy()?.isNotEmpty() == true } as? UIPanelAPI
    }

    fun getCaptainPickerDialog(): CaptainPickerDialog? {
        val core = getCoreUI() ?: return null
        val children = core.safeInvoke("getChildrenNonCopy") as? MutableList<*> ?: return null
        return children.firstOrNull { it is CaptainPickerDialog } as? CaptainPickerDialog
    }

    fun getViewedFleetInFleetPanel(
    ): FleetDataAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            val fleetPanel = getFleetPanel() ?: return null

            return fleetPanel.safeInvoke("getFleetData") as? FleetDataAPI
        }
        return null
    }

    fun getSelectedSubmarket(
    ): SubmarketAPI? {
        val campaignUI = Global.getSector().campaignUI

        if (campaignUI.isShowingDialog) {
            if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
                val dialog = campaignUI.currentInteractionDialog ?: return null
                dialog.interactionTarget?.market ?: return null

                val fleetTab = getFleetTab() ?: return null

                return fleetTab
                    .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                    .getOrNull(0)
                    ?.get(fleetTab) as? SubmarketAPI
            } else if (campaignUI.getActualCurrentTab() == CoreUITabId.CARGO) {
                val dialog = campaignUI.currentInteractionDialog ?: return null
                dialog.interactionTarget?.market ?: return null

                val cargoPanel = getCargoPanel() ?: return null
                val transferHandler = cargoPanel.safeInvoke("getTransferHandler") ?: return null

                return transferHandler
                    .getFieldsMatching(fieldAssignableTo = Submarket::class.java)
                    .getOrNull(0)
                    ?.get(transferHandler) as? SubmarketAPI
            }
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
        if (Global.getSector().campaignUI?.getActualCurrentTab() != CoreUITabId.FLEET) return

        var fleetPanel: UIPanelAPI? = null
        try {
            val fleetTab = getFleetTab()
            if (fleetTab != null) {
                fleetPanel = fleetTab.safeInvoke(name = "getFleetPanel") as? UIPanelAPI // This is more safe
            }
        } catch (_: Exception) {
        }
        fleetPanel?.safeInvoke("updateListContents")

        postUpdateFleetPanelCallbacks.forEach { it.invoke() }
    }

    fun getFleetScreenPickedUpMember(): Any? {
        val fleetPanel = getFleetPanel()
        val clickAndDropHandler = fleetPanel?.safeInvoke("getClickAndDropHandler")
        return clickAndDropHandler?.safeInvoke("getPickedUpMember")
    }
}