package fleetBuilder.ui.customPanel.patterns

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.parent
import fleetBuilder.ui.customPanel.core.ModalPanel

class ContextMenuPanel : ModalPanel() {
    companion object {
        val contextMenuPanels = mutableListOf<ContextMenuPanel>()

        /**
         * Returns true if the context menu is open or was just closed
         */
        fun isContextMenuOpen(): Boolean {
            contextMenuPanels.removeIf { it.panel.parent == null }

            return contextMenuJustClosed > 0 || contextMenuPanels.isNotEmpty()
        }

        internal var contextMenuJustClosed = 0
    }

    override var consumeAllEvents = false
    override var anyOuterMouseClickQuits = true
    override var hotkeyQuitConsumesInput = false

    init {
        background.alphaMult = 1f
        contextMenuPanels.add(this)
        setMouseCapturePad(-4f)
    }

    override fun forceDismiss(runExitScript: Boolean) {
        super.forceDismiss(runExitScript)
        contextMenuPanels.remove(this)
        contextMenuJustClosed = 2
    }

    override fun present(width: Float, height: Float, parent: UIPanelAPI?, xOffset: Float?, yOffset: Float?) {
        val xOffset = xOffset ?: Global.getSettings().mouseX.toFloat()
        val yOffset = yOffset ?: Global.getSettings().mouseY.toFloat()

        super.present(width, height, parent, xOffset, yOffset)
    }
}