package fleetBuilder.util.api

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.UIComponentAPI

object UIUtils {
    fun isMouseHoveringOverComponent(component: UIComponentAPI, pad: Float = 0f): Boolean {
        val x = component.position.x - pad
        val y = component.position.y - pad
        val width = component.position.width + pad * 2
        val height = component.position.height + pad * 2

        return isMouseWithinBounds(x, y, width, height)
    }

    fun isMouseWithinBounds(x: Float, y: Float, width: Float, height: Float): Boolean {
        val settings = Global.getSettings()
        val mouseX = settings.mouseX
        val mouseY = settings.mouseY

        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height
    }
}