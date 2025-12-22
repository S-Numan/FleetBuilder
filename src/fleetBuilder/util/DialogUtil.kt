package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.popUpUI.PopUpUI
import starficz.getChildrenCopy

class DialogUtil : EveryFrameCombatPlugin {
    companion object {
        fun initPopUpUI(
            dialog: PopUpUI,
            width: Float,
            height: Float,
            parent: UIPanelAPI? = null,
            x: Float? = null,
            y: Float? = null
        ) {
            val gameState = Global.getCurrentState()
            if (gameState == null) {
                prependDialogToShow(dialog, width, height) // To create a dialog before the game has fully booted up, so it is shown on start
            } else {
                val coreUI = ReflectionMisc.getCoreUI(true) ?: return

                initDialog(coreUI, x = x, y = y, width = width, height = height, dialog = dialog, parent = parent)
            }
        }

        fun isPopUpUIOpen(): Boolean {
            if (dialogsToShow.isNotEmpty()) return true

            ReflectionMisc.getCoreUI(true)?.getChildrenCopy()?.forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is PopUpUI)
                ) {
                    return true
                }
            }
            return false
        }

        private fun prependDialogToShow(dialog: PopUpUI, width: Float, height: Float) {
            dialogsToShow.add(0, Triple(dialog, width, height))
        }

        private val dialogsToShow: MutableList<Triple<PopUpUI, Float, Float>> = mutableListOf()

        private fun initDialog(
            coreUI: UIPanelAPI,
            dialog: PopUpUI,
            width: Float,
            height: Float,
            parent: UIPanelAPI? = null,
            x: Float? = null,
            y: Float? = null
        ) {
            //if (Global.getCurrentState() == GameState.CAMPAIGN && !Global.getSector().isPaused)
            //    Global.getSector().isPaused = true

            if (Global.getCurrentState() == GameState.COMBAT && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
                Global.getCombatEngine().isPaused = true

            val panelAPI = Global.getSettings().createCustom(width, height, dialog)
            dialog.init(
                panelAPI,
                x ?: (coreUI.position.centerX - width / 2),
                y ?: (coreUI.position.centerY + height / 2),
                parent ?: coreUI
            )
        }
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {
        if (dialogsToShow.isNotEmpty()) {
            val coreUI = ReflectionMisc.getCoreUI(true) ?: return

            val (dialog, width, height) = dialogsToShow.removeAt(dialogsToShow.size - 1)

            initDialog(coreUI, dialog, width, height)
        }
    }

    override fun processInputPreCoreControls(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {
    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {}

    override fun renderInUICoords(viewport: ViewportAPI?) {}

    @Deprecated("Deprecated in Java")
    override fun init(engine: CombatEngineAPI?) {
    }
}