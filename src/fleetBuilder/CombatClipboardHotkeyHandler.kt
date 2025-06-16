package fleetBuilder

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.input.InputEventType
import fleetBuilder.ModSettings.fleetClipboardHotkeyHandler
import fleetBuilder.misc.ClipboardStuff.codexEntryToClipboard
import fleetBuilder.misc.MISC.getCodexDialog
import fleetBuilder.misc.MISC.showError
import org.lwjgl.input.Keyboard

class CombatClipboardHotkeyHandler : EveryFrameCombatPlugin {
    override fun processInputPreCoreControls(
        amount: Float,
        events: List<InputEventAPI>
    ) {
        if(!fleetClipboardHotkeyHandler) return

        for (event in events) {
            if (event.isConsumed) continue
            if (event.eventType == InputEventType.KEY_DOWN) {
                if (event.isCtrlDown) {
                    if (event.eventValue == Keyboard.KEY_C) {
                        try {
                            val codex = getCodexDialog()

                            if (codex != null) {
                                codexEntryToClipboard(codex)
                                event.consume(); continue
                            }
                        } catch (e: Exception) {
                            showError("FleetBuilder hotkey failed", e)
                        }
                    }
                }
            }
        }
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {

    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {

    }

    override fun renderInUICoords(viewport: ViewportAPI?) {

    }

    override fun init(engine: CombatEngineAPI?) {

    }

}