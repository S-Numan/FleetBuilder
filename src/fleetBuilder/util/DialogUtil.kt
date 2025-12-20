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
import fleetBuilder.ui.popUpUI.old.PopUpUI_OLD
import starficz.getChildrenCopy

class DialogUtil : EveryFrameCombatPlugin {
    companion object {
        fun initPopUpUI(dialog: PopUpUI, width: Float, height: Float) {
            val gameState = Global.getCurrentState()
            if (gameState == null) {
                prependDialogToShow(dialog, width, height) // To create a dialog before the game has fully booted up, so it is shown on start
            } else {
                val coreUI = ReflectionMisc.getCoreUI(true) ?: return

                initDialog(coreUI, dialog, width, height)
            }
        }

        fun isPopUpUIOpen(): Boolean {
            if (dialogsToShow.isNotEmpty()) return true

            ReflectionMisc.getCoreUI(true)?.getChildrenCopy()?.forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is PopUpUI
                            || child.plugin is PopUpUI_OLD)//TODO, REMOVE THIS LINE WHEN POSSIBLE
                ) {
                    return true
                }
            }
            return false
        }

        //TODO, REMOVE ME WHEN POSSIBLE
        fun initPopUpUI(dialog: PopUpUI_OLD, width: Float, height: Float) {
            val coreUI = ReflectionMisc.getCoreUI(true) ?: return

            if (Global.getCurrentState() == GameState.CAMPAIGN && !Global.getSector().isPaused)
                Global.getSector().isPaused = true

            if (Global.getCurrentState() == GameState.COMBAT && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
                Global.getCombatEngine().isPaused = true

            val panelAPI = Global.getSettings().createCustom(width, height, dialog)
            dialog.init(
                panelAPI,
                coreUI.position.centerX - panelAPI.position.width / 2,
                coreUI.position.centerY + panelAPI.position.height / 2,
            )


            /*  //Top Left
                dialog.init(
                    panelAPI,
                    0f,
                    coreUI.position.height,
                )
                dialog.isDialog = false
            */
        }

        private fun prependDialogToShow(dialog: PopUpUI, width: Float, height: Float) {
            dialogsToShow.add(0, Triple(dialog, width, height))
        }

        private val dialogsToShow: MutableList<Triple<PopUpUI, Float, Float>> = mutableListOf()

        private fun initDialog(coreUI: UIPanelAPI, dialog: PopUpUI, width: Float, height: Float) {
            //if (Global.getCurrentState() == GameState.CAMPAIGN && !Global.getSector().isPaused)
            //    Global.getSector().isPaused = true

            if (Global.getCurrentState() == GameState.COMBAT && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
                Global.getCombatEngine().isPaused = true

            val panelAPI = Global.getSettings().createCustom(width, height, dialog)
            dialog.init(
                panelAPI,
                coreUI.position.centerX - panelAPI.position.width / 2,
                coreUI.position.centerY + panelAPI.position.height / 2,
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