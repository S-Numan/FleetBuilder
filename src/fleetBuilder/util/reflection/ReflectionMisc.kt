package fleetBuilder.util.reflection

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.FleetDataAPI
import com.fs.starfarer.api.ui.IntelUIAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.campaign.ui.UITable
import com.fs.starfarer.codex2.CodexDetailPanel
import com.fs.starfarer.codex2.CodexDialog
import com.fs.starfarer.coreui.CaptainPickerDialog
import com.fs.state.AppDriver
import fleetBuilder.otherMods.starficz.ReflectionUtils.get
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import fleetBuilder.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilder.otherMods.starficz.findChildWithMethod
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.util.api.kotlin.safeInvoke
import org.magiclib.util.api.getActualCurrentTab

object ReflectionMisc {

    @JvmStatic
    fun getScreenPanel(): UIPanelAPI? {
        val state = AppDriver.getInstance().currentState
        return state.safeInvoke("getScreenPanel") as? UIPanelAPI
    }

    // Tip: Core UI is a child of the screen panel
    @JvmStatic
    fun getCoreUI(): CoreUIAPI? {
        val state = AppDriver.getInstance().currentState
        if (state is CampaignUIAPI) {
            return (state.currentInteractionDialog?.let { dialog ->
                dialog.safeInvoke("getCoreUI") as? CoreUIAPI
            } ?: state.safeInvoke("getCore") as? CoreUIAPI)
        }// else if (state is TitleScreenState || state is CombatState) {
        //  return null
        //  }
        return null
    }

    @JvmOverloads
    @JvmStatic
    fun getBorderContainer(coreUI: CoreUIAPI? = getCoreUI()): UIPanelAPI? {
        return (coreUI as? UIPanelAPI)?.findChildWithMethod("setBorderInsetLeft") as? UIPanelAPI
    }

    @JvmOverloads
    @JvmStatic
    fun getCurrentTab(coreUI: CoreUIAPI? = getCoreUI()): UIPanelAPI? {
        return coreUI?.safeInvoke("getCurrentTab") as? UIPanelAPI
    }

    @JvmStatic
    fun getRefitTab(): UIPanelAPI? {
        if (Global.getCurrentState() == GameState.CAMPAIGN) {
            return if (Global.getSector()?.campaignUI?.getActualCurrentTab() == CoreUITabId.REFIT)
                getCurrentTab()
            else
                null
            //return getBorderContainer()?.findChildWithMethod("goBackToParentIfNeeded") as? UIPanelAPI
        } else { // Get title-screen mission refit tab
            val delegateChild = getScreenPanel()?.findChildWithMethod("dismiss") as? UIPanelAPI ?: return null
            val oldCoreUI = delegateChild.findChildWithMethod("getMissionInstance") as? UIPanelAPI ?: return null
            val holographicBG = oldCoreUI.findChildWithMethod("forceFoldIn") ?: return null

            return holographicBG.safeInvoke("getCurr") as? UIPanelAPI
        }
    }

    @JvmStatic
    fun getIntelTab(): UIPanelAPI? {
        return if (Global.getSector()?.campaignUI?.getActualCurrentTab() != CoreUITabId.INTEL)
            null
        else
            getCurrentTab()
    }

    @JvmStatic
    fun getFleetTab(): UIPanelAPI? {
        return if (Global.getSector()?.campaignUI?.getActualCurrentTab() != CoreUITabId.FLEET)
            null
        else
            getCurrentTab()
    }

    fun getCargoTab(): UIPanelAPI? {
        return if (Global.getSector()?.campaignUI?.getActualCurrentTab() != CoreUITabId.CARGO)
            null
        else
            getCurrentTab()
    }

    @JvmOverloads
    @JvmStatic
    fun getIntelUI(intelTab: UIPanelAPI? = getIntelTab()): IntelUIAPI? {
        return intelTab?.safeInvoke("getEventsPanel") as? IntelUIAPI
    }

    @JvmOverloads
    @JvmStatic
    fun getCargoPanel(cargoTab: UIPanelAPI? = getCargoTab()): UIPanelAPI? {
        return cargoTab?.findChildWithMethod("shouldShowLogisticsOnSwitch") as? UIPanelAPI
        //val transferHandler = cargoTabChild?.invoke("getTransferHandler")// Howto get Cargo drawn when picked up with the mouse

        //Alternative method
        //val border = ReflectionMisc.getBorderContainer()
        //val cargoTab = border?.findChildWithMethod("shouldShowLogisticsOnSwitch") as? UIPanelAPI ?: return null
    }

    @JvmOverloads
    @JvmStatic
    fun getFleetPanel(fleetTab: UIPanelAPI? = getFleetTab()): UIPanelAPI? {
        return fleetTab?.findChildWithMethod("getOther") as? UIPanelAPI
    }

    @JvmStatic
    fun getViewedFleetInFleetPanel(): FleetDataAPI? {
        val campaignUI = Global.getSector()?.campaignUI ?: return null

        if (campaignUI.getActualCurrentTab() == CoreUITabId.FLEET) {
            val fleetPanel = getFleetPanel() ?: return null

            return fleetPanel.safeInvoke("getFleetData") as? FleetDataAPI
        }
        return null
    }

    @JvmOverloads
    @JvmStatic
    fun getFleetSidePanel(fleetTab: UIPanelAPI? = getFleetTab()): UIPanelAPI? {
        val children = fleetTab?.getChildrenCopy()
        return children?.find { it.getFieldsMatching(type = UITable::class.java).isNotEmpty() } as? UIPanelAPI
    }

    @JvmOverloads
    @JvmStatic
    fun getFleetPanelPickedUpMember(fleetPanel: UIPanelAPI? = getFleetPanel()): Any? {
        val fleetPanel = fleetPanel
        val clickAndDropHandler = fleetPanel?.safeInvoke("getClickAndDropHandler")
        return clickAndDropHandler?.safeInvoke("getPickedUpMember")
    }

    private var postUpdateFleetPanelCallbacks = mutableListOf<() -> Unit>()

    @JvmStatic
    fun addPostUpdateFleetPanelCallback(callback: () -> Unit) {
        if (postUpdateFleetPanelCallbacks.contains(callback)) return

        postUpdateFleetPanelCallbacks.add(callback)
    }

    @JvmStatic
    fun removePostUpdateFleetPanelCallback(callback: () -> Unit) {
        postUpdateFleetPanelCallbacks.remove(callback)
    }

    @JvmStatic
    fun updateFleetPanelContents() {
        if (Global.getSector()?.campaignUI?.getActualCurrentTab() != CoreUITabId.FLEET) return

        getFleetPanel()?.safeInvoke("updateListContents")

        postUpdateFleetPanelCallbacks.forEach { it.invoke() }
    }

    @JvmStatic
    fun isCodexOpen(): Boolean {
        if (Global.getSettings().isShowingCodex) return true

        val gameState = Global.getCurrentState()

        // F2 while hovering over ship in the fleet screen. Clicking the question mark in the fleet screen. Does not include hovering over the question mark and pressing F2
        if (gameState == GameState.CAMPAIGN && Global.getSector()?.campaignUI?.getActualCurrentTab() == CoreUITabId.FLEET) {
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

    @JvmStatic
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

        if (gameState == GameState.CAMPAIGN && Global.getSector()?.campaignUI?.getActualCurrentTab() == CoreUITabId.FLEET) {
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

    @JvmStatic
    fun getCodexDetailPanel(codex: CodexDialog): UIPanelAPI? {
        return codex.get(type = CodexDetailPanel::class.java) as? UIPanelAPI
    }

    @JvmStatic
    fun getCodexEntryParam(codex: CodexDialog): Any? {
        val codexDetailPanel = getCodexDetailPanel(codex) ?: return null
        val codexEntry = codexDetailPanel.get(name = "plugin") ?: return null

        return codexEntry.safeInvoke("getParam")
    }

    @JvmOverloads
    @JvmStatic
    fun getCaptainPickerDialog(coreUI: CoreUIAPI? = getCoreUI()): CaptainPickerDialog? {
        val children = coreUI?.safeInvoke("getChildrenNonCopy") as? MutableList<*> ?: return null
        return children.firstOrNull { it is CaptainPickerDialog } as? CaptainPickerDialog
    }
}