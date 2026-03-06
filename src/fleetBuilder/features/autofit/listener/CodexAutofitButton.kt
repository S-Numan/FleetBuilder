package fleetBuilder.features.autofit.listener

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.ModSettings
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.features.autofit.ui.AutofitPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.addShortcutNoShow
import fleetBuilder.util.createHullVariant
import starficz.*

class CodexAutofitButton : EveryFrameScript, BaseEveryFrameCombatPlugin() {
    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true


    override fun advance(amount: Float) {
        if (Global.getSector().isPaused)
            onAdvance()
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>) {
        if (ModSettings.autofitMenuEnabled && ModSettings.codexAutofitButton && Global.getCombatEngine()?.isPaused == true || Global.getCurrentState() == GameState.TITLE)
            onAdvance()
    }

    var param: Any? = null
    var openAutofitButton: ButtonAPI? = null
    var autofitPanel: UIPanelAPI? = null
    fun onAdvance() {
        if (!ReflectionMisc.isCodexOpen()) {
            this.param = null
            openAutofitButton = null
            autofitPanel = null
            return
        }

        val codex = ReflectionMisc.getCodexDialog() ?: run {
            DisplayMessage.showError("Code should not reach here")
            return
        }
        val param = ReflectionMisc.getCodexEntryParam(codex)

        if (this.param === param)
            return
        // Param change detected (may also occur on codex opening)

        val codexDetailPanel = ReflectionMisc.getCodexDetailPanel(codex) ?: return

        // Remove any previous button
        if (openAutofitButton != null) {
            codexDetailPanel.removeComponent(openAutofitButton)
            codex.removeComponent(autofitPanel)
            openAutofitButton = null
            autofitPanel = null
        }

        // Get variant (if possible)
        val variant = when (param) {
            is ShipHullSpecAPI -> {
                param.createHullVariant()
            }
            is FleetMemberAPI -> {
                param.variant
            }
            else -> null
        }

        if (variant != null) {
            val buttonText = "Autofit"
            openAutofitButton = codexDetailPanel.addButton(
                buttonText,
                null,
                Misc.getButtonTextColor(),
                Misc.getDarkPlayerColor(),
                Alignment.MID,
                CutStyle.NONE,
                24f, 18f,
                Font.ORBITRON_20
            )
            openAutofitButton!!.width = Global.getSettings().computeStringWidth(buttonText, Fonts.ORBITRON_20AA) + 12f

            val belowTitleBarDeeperPanel = ReflectionMisc.getBelowTitleDeeperPanel(codex)
            val shipDisplay = belowTitleBarDeeperPanel?.findChildWithMethod("isSchematicMode") as? UIPanelAPI ?: return

            val leftPanelSize = belowTitleBarDeeperPanel.x - codex.x

            openAutofitButton!!.xAlignOffset = -codexDetailPanel.x + shipDisplay.centerX - openAutofitButton!!.width / 2f
            //openAutofitButton!!.yAlignOffset = -openAutofitButton!!.height + 1f

            openAutofitButton!!.addShortcutNoShow(ModSettings.autofitMenuHotkey)
            openAutofitButton!!.onClick {
                autofitPanel = AutofitPanel.createMagicAutofitPanel(codex, codex.width - leftPanelSize, codex.height, variant, false)
                codex.addComponent(autofitPanel).setXAlignOffset(leftPanelSize)
            }
        }

        this.param = param
    }
}