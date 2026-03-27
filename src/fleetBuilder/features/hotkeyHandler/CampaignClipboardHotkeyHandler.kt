package fleetBuilder.features.hotkeyHandler

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.pasteFleet
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleCaptainPickerMouseEvents
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleCreateOfficer
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleDevModeHotkey
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleFleetMouseEvents
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleInteractionCopy
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleRefitCopy
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleRefitMouseEvents
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleRefitPaste
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleSaveTransfer
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.handleUIFleetCopy
import fleetBuilder.features.hotkeyHandler.ClipboardHotkeyHandlerUtils.pasteIntoPlayerFleetPanel
import fleetBuilder.serialization.ClipboardMisc
import fleetBuilder.serialization.MissingElements
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.getActualCurrentTab
import fleetBuilder.util.isIdle
import org.lwjgl.input.Keyboard


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
        if (!event.isCtrlDown || DialogUtils.isPopUpPanelOpen()) return

        when (event.eventValue) {
            Keyboard.KEY_D -> if (event.isShiftDown) handleDevModeHotkey(event)
            Keyboard.KEY_C -> handleCopyHotkey(event, sector, ui)
            Keyboard.KEY_V -> handlePasteHotkey(event, ui, sector)
            Keyboard.KEY_O -> handleCreateOfficer(event, ui)
            Keyboard.KEY_I -> handleSaveTransfer(event, ui)
        }
    }

    private fun handleMouseDownEvents(
        event: InputEventAPI,
        ui: CampaignUIAPI
    ) {
        if (ReflectionMisc.isCodexOpen() || DialogUtils.isPopUpPanelOpen()) return

        val tab = ui.getActualCurrentTab() ?: return
        val isCtrlLmb = event.isCtrlDown && event.isLMBDownEvent

        if (tab == CoreUITabId.REFIT || tab == CoreUITabId.FLEET) {
            val captainPicker = ReflectionMisc.getCaptainPickerDialog()

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
        val codex = ReflectionMisc.getCodexDialog()

        when {
            codex != null -> {
                ClipboardMisc.codexEntryToClipboard(codex)
                event.consume()
            }
            ui.getActualCurrentTab() == CoreUITabId.FLEET -> if (handleUIFleetCopy(sector, event.isShiftDown)) event.consume()
            ui.getActualCurrentTab() == CoreUITabId.REFIT -> if (handleRefitCopy(event.isShiftDown)) event.consume()
            ui.currentInteractionDialog != null -> if (handleInteractionCopy(ui, event.isAltDown, event.isShiftDown)) event.consume()
        }
    }

    private fun handlePasteHotkey(
        event: InputEventAPI,
        ui: CampaignUIAPI,
        sector: SectorAPI
    ) {
        if (ReflectionMisc.isCodexOpen()) return

        if (ui.getActualCurrentTab() == CoreUITabId.REFIT) {
            if (handleRefitPaste()) event.consume()
        } else {
            handleOtherPaste(event, sector, ui)
        }
    }

    private fun handleOtherPaste(
        event: InputEventAPI,
        sector: SectorAPI,
        ui: CampaignUIAPI
    ) {
        val missing = MissingElements()
        val data = ClipboardMisc.extractDataFromClipboard(missing) ?: return

        if (ui.getActualCurrentTab() == CoreUITabId.FLEET || ui.isIdle()) {
            if (data is DataFleet.ParsedFleetData)
                pasteFleet(data, missing)
            else if (ui.getActualCurrentTab() == CoreUITabId.FLEET && ClipboardHotkeyHandlerUtils.requireCheatsOrWarn())
                pasteIntoPlayerFleetPanel(sector, data, missing)
        }

        event.consume()
    }

    override fun processCampaignInputPreFleetControl(events: MutableList<InputEventAPI>) = Unit

    override fun processCampaignInputPostCore(events: MutableList<InputEventAPI>) = Unit
}