package fleetkit.features.hotkeyHandler

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleCaptainPickerMouseEvents
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleCreateOfficer
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleDevModeHotkey
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleFleetMouseEvents
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleInteractionCopy
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleRefitCopy
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleRefitMouseEvents
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleRefitPaste
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleSaveTransfer
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleUIFleetCopy
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.pasteFleet
import fleetkit.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.pasteIntoPlayerFleetPanel
import fleetkit.features.recentBattles.RecentBattleDialog
import fleetkit.serialization.ClipboardMisc
import fleetkit.serialization.MissingContent
import fleetkit.serialization.fleet.DataFleet
import fleetkit.ui.customPanel.DialogUtils
import fleetkit.util.api.kotlin.isIdle
import org.lwjgl.input.Keyboard
import org.magiclib.util.api.getActualCurrentTab
import org.magiclib.util.reflection.UIFinder

internal class CampaignClipboardHotkeyHandler : CampaignInputListener {
    override fun getListenerInputPriority(): Int = 1

    override fun processCampaignInputPreCore(events: MutableList<InputEventAPI>) {
        val sector = Global.getSector() ?: return
        val ui = sector.campaignUI ?: return

        for (event in events) {
            if (event.isConsumed) continue

            when (event.eventType) {
                InputEventType.KEY_DOWN ->
                    handleKeyDownEvents(event, sector, ui)

                InputEventType.MOUSE_DOWN ->
                    handleMouseDownEvents(event, ui)

                else -> Unit
            }
        }
    }

    private fun handleKeyDownEvents(
        event: InputEventAPI,
        sector: SectorAPI,
        ui: CampaignUIAPI
    ) {
        if (!event.isCtrlDown) return

        when (event.eventValue) {
            Keyboard.KEY_D -> if (event.isShiftDown) handleDevModeHotkey(event)
            Keyboard.KEY_C -> handleCopyHotkey(event, sector, ui)
            Keyboard.KEY_V -> handlePasteHotkey(ui, event)
            Keyboard.KEY_O -> handleCreateOfficer(event, ui)
            Keyboard.KEY_I -> handleSaveTransfer(event, ui)
            Keyboard.KEY_R -> RecentBattleDialog.recentBattleDialog(event, ui)
        }

    }

    private fun handleMouseDownEvents(
        event: InputEventAPI,
        ui: CampaignUIAPI
    ) {
        if (Global.getSettings().isShowingCodex || DialogUtils.isModalPanelOpen()) return

        val tab = ui.getActualCurrentTab() ?: return
        val isCtrlLmb = event.isCtrlDown && event.isLMBDownEvent

        if (tab == CoreUITabId.REFIT || tab == CoreUITabId.FLEET) {
            val captainPicker = UIFinder.getCaptainPickerDialog()

            if (captainPicker != null) {
                if (isCtrlLmb)
                    handleCaptainPickerMouseEvents(event, captainPicker)
            } else if (tab == CoreUITabId.REFIT) {
                if (isCtrlLmb)
                    handleRefitMouseEvents(event)
            } else {
                handleFleetMouseEvents(event)
            }
        }
    }

    private fun handleCopyHotkey(
        event: InputEventAPI,
        sector: SectorAPI,
        ui: CampaignUIAPI
    ) {
        val codex = UIFinder.getCodexDialog()
        if (codex != null) {
            ClipboardMisc.codexEntryToClipboard(codex)
            event.consume()
            return
        }
        if (DialogUtils.isModalPanelOpen())
            return

        when {
            ui.getActualCurrentTab() == CoreUITabId.FLEET -> if (handleUIFleetCopy(sector, event.isShiftDown)) event.consume()
            ui.getActualCurrentTab() == CoreUITabId.REFIT -> if (handleRefitCopy(event.isShiftDown)) event.consume()
            ui.currentInteractionDialog != null -> if (handleInteractionCopy(ui, event.isAltDown, event.isShiftDown)) event.consume()
        }
    }

    internal companion object {
        internal fun handlePasteHotkey(
            ui: CampaignUIAPI,
            event: InputEventAPI?,
        ) {
            if (Global.getSettings().isShowingCodex || DialogUtils.isModalPanelOpen()) return

            val currentTab = ui.getActualCurrentTab()

            if (currentTab == CoreUITabId.REFIT) {
                if (handleRefitPaste()) event?.consume()
            } else {
                val missing = MissingContent()
                val data = ClipboardMisc.extractDataFromClipboard(missing) ?: return

                if (currentTab == CoreUITabId.FLEET || ui.isIdle()) {
                    if (currentTab != CoreUITabId.FLEET || data is DataFleet.ParsedFleetData)
                        pasteFleet(data, missing)
                    else if (ClipboardHotkeyHandlerUtils.requireCheatsOrWarn())
                        pasteIntoPlayerFleetPanel(data, missing)
                }

                event?.consume()
            }
        }
    }

    override fun processCampaignInputPreFleetControl(events: MutableList<InputEventAPI>) = Unit

    override fun processCampaignInputPostCore(events: MutableList<InputEventAPI>) = Unit
}