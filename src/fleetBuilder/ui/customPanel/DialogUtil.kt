package fleetBuilder.ui.customPanel

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.customPanel.common.PopUpPanel
import fleetBuilder.util.ReflectionMisc
import starficz.getChildrenCopy

class DialogUtil : EveryFrameCombatPlugin {
    companion object {
        fun initPopUpUI(
            dialog: PopUpPanel,
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
                val screenPanel = ReflectionMisc.getScreenPanel() ?: return

                dialog.init(
                    width = width,
                    height = height,
                    x = x
                        ?: parent?.let { parent.position.centerX - width / 2 }
                        ?: (screenPanel.position.centerX - width / 2),
                    y = y
                        ?: parent?.let { parent.position.centerY - height / 2 }
                        ?: (screenPanel.position.centerY + height / 2),
                    parent ?: screenPanel
                )
            }
        }

        fun isPopUpUIOpen(): Boolean {
            if (dialogsToShow.isNotEmpty()) return true

            ReflectionMisc.getScreenPanel()?.getChildrenCopy()?.forEach { child ->
                if (child is CustomPanelAPI && (child.plugin is PopUpPanel)
                ) {
                    return true
                }
            }
            return false
        }

        fun prependDialogToShow(dialog: PopUpPanel, width: Float, height: Float) {
            dialogsToShow.add(0, Triple(dialog, width, height))
        }

        private val dialogsToShow: MutableList<Triple<PopUpPanel, Float, Float>> = mutableListOf()
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {
        if (dialogsToShow.isNotEmpty() && !isPopUpUIOpen()) {
            val screenPanel = ReflectionMisc.getScreenPanel() ?: return

            val (dialog, width, height) = dialogsToShow.removeAt(dialogsToShow.size - 1)

            initPopUpUI(dialog, width, height, screenPanel)
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