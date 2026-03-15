package fleetBuilder.ui.customPanel

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.ui.customPanel.common.BasePanel
import fleetBuilder.ui.customPanel.common.ModalPanel
import fleetBuilder.util.ReflectionMisc
import starficz.getChildrenCopy

class DialogUtils : BaseEveryFrameCombatPlugin() {
    companion object {
        fun initDialogToShow(
            dialog: BasePanel,
            width: Float,
            height: Float,
            parent: UIPanelAPI? = null,
            xOffset: Float? = null,
            yOffset: Float? = null
        ) {
            val gameState = Global.getCurrentState()
            if (gameState == null) {
                prependDialogToShow(dialog, width, height) // To create a dialog before the game has fully booted up, so it is shown on start
            } else {
                val parent = parent ?: ReflectionMisc.getScreenPanel() ?: return

                dialog.init(
                    width = width,
                    height = height,
                    xOffset = xOffset ?: (parent.position.centerX - width / 2),
                    yOffset = yOffset ?: (parent.position.centerY + height / 2),
                    parent
                )
            }
        }

        fun isPopUpPanelOpen(): Boolean {
            ReflectionMisc.getScreenPanel()?.getChildrenCopy()?.forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is ModalPanel)
                ) {
                    return true
                }
            }
            return false
        }

        fun prependDialogToShow(dialog: BasePanel, width: Float, height: Float) {
            dialogsToShow.add(0, Triple(dialog, width, height))
        }

        private val dialogsToShow: MutableList<Triple<BasePanel, Float, Float>> = mutableListOf()

        fun forceCloseAllDialogs(): Boolean {
            var closedOne = false
            val screenPanel = ReflectionMisc.getScreenPanel()
            screenPanel?.getChildrenCopy()?.toList()?.forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is BasePanel)) {
                    try {
                        (child.plugin as BasePanel).forceDismiss()
                    } catch (e: Exception) {
                        DisplayMessage.showError("Error when force dismissing dialog\n$e")
                    }
                    screenPanel.removeComponent(child)
                    closedOne = true
                }
            }
            return closedOne
        }
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {
        if (dialogsToShow.isNotEmpty()) {
            val screenPanel = ReflectionMisc.getScreenPanel() ?: return

            val (dialog, width, height) = dialogsToShow.removeAt(dialogsToShow.size - 1)

            initDialogToShow(dialog, width, height, screenPanel)
        }
    }
}