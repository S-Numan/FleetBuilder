package fleetBuilder.ui.customPanel

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.util.DisplayMessage
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.ui.customPanel.core.BasePanel
import fleetBuilder.ui.customPanel.core.ModalPanel
import fleetBuilder.ui.customPanel.patterns.ContextMenuPanel
import fleetBuilder.util.ReflectionMisc

class DialogUtils : BaseEveryFrameCombatPlugin(), EveryFrameScript {
    companion object {
        fun initPanelToShow(
            dialog: BasePanel,
            width: Float = 800f,
            height: Float = 800f,
            parent: UIPanelAPI? = null,
            xOffset: Float? = null,
            yOffset: Float? = null
        ) {
            val gameState = Global.getCurrentState()
            if (gameState == null) {
                prependPanelToShow(dialog, width, height) // To create a dialog before the game has fully booted up, so it is shown on start
            } else {
                val parent = parent ?: ReflectionMisc.getScreenPanel() ?: run {
                    DisplayMessage.showError("Failed to get Screen Panel")
                    return
                }

                dialog.init(
                    width = width,
                    height = height,
                    xOffset = xOffset ?: (parent.position.centerX - width / 2),
                    yOffset = yOffset ?: (parent.position.centerY + height / 2),
                    parent
                )
            }
        }

        fun isModalPanelOpen(): Boolean {
            ReflectionMisc.getScreenPanel()?.getChildrenCopy()?.forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is ModalPanel)
                ) {
                    return true
                }
            }
            return false
        }

        fun prependPanelToShow(dialog: BasePanel, width: Float, height: Float) {
            panelsToShow.add(0, Triple(dialog, width, height))
        }

        private val panelsToShow: MutableList<Triple<BasePanel, Float, Float>> = mutableListOf()

        fun forceCloseAllCustomPanels(): Boolean {
            var closedOne = false
            val screenPanel = ReflectionMisc.getScreenPanel() ?: return false
            screenPanel.getChildrenCopy().toList().forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is BasePanel)) {
                    try {
                        (child.plugin as BasePanel).forceDismiss()
                    } catch (e: Exception) {
                        DisplayMessage.showError("Error when force dismissing panel\n$e")
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
        advance()
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true
    override fun advance(amount: Float) {
        advance()
    }

    fun advance() {
        if (ContextMenuPanel.contextMenuJustClosed > 0)
            ContextMenuPanel.contextMenuJustClosed--

        if (panelsToShow.isNotEmpty()) {
            val screenPanel = ReflectionMisc.getScreenPanel() ?: return

            val (dialog, width, height) = panelsToShow.removeAt(panelsToShow.size - 1)

            initPanelToShow(dialog, width, height, screenPanel)
        }
    }
}