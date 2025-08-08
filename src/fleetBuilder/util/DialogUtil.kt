package fleetBuilder.util

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.ui.popUpUI.PopUpUI
import starficz.getChildrenCopy

object DialogUtil {
    fun initPopUpUI(dialog: PopUpUI, width: Float, height: Float) {
        val coreUI = ReflectionMisc.getCoreUI() ?: return

        if (!Global.getSector().isPaused)
            Global.getSector().isPaused = true

        if (Global.getCurrentState() != GameState.TITLE && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
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

    fun isPopUpUIOpen(): Boolean {
        ReflectionMisc.getCoreUI()?.getChildrenCopy()?.forEach { child ->
            if (child is CustomPanelAPI && child.plugin is PopUpUI) {
                return true
            }
        }
        return false
    }
}