package fleetBuilder.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.otherMods.starficz.x
import fleetBuilder.otherMods.starficz.y
import fleetBuilder.util.kotlin.withAlphaMult
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.alphaf
import org.magiclib.kotlin.bluef
import org.magiclib.kotlin.greenf
import org.magiclib.kotlin.redf
import java.awt.Color

object UIUtils {

    @JvmOverloads
    fun playSound(id: String, volume: Float = 1f, pitch: Float = 1f) {
        Global.getSoundPlayer().playUISound(id, pitch, volume)
    }

    fun drawBorder(x1: Float, y1: Float, x2: Float, y2: Float) {
        GL11.glRectf(x1, y1, x2 + 1, y1 - 1)
        GL11.glRectf(x2, y1, x2 + 1, y2 + 1)
        GL11.glRectf(x1, y2, x1 - 1, y1 - 1)
        GL11.glRectf(x2, y2, x1 - 1, y2 + 1)
    }

    fun easeCubic(t: Float): Float {
        return t * t * t
    }

    fun drawRectangleFilledForTooltip(tooltipMakerAPI: TooltipMakerAPI, alphaMult: Float, uiColor: Color) {
        drawRectangleFilledForPos(tooltipMakerAPI.x, tooltipMakerAPI.y, tooltipMakerAPI.width, tooltipMakerAPI.height, alphaMult, uiColor)
    }

    fun drawRectangleFilledForPos(x: Float, y: Float, w: Float, h: Float, alphaMult: Float, uiColor: Color) {
        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glColor4f(
            uiColor.red / 255f, uiColor.green / 255f, uiColor.blue / 255f,
            uiColor.alpha / 255f * alphaMult * 23f
        )
        GL11.glRectf(x, y, x + w, y + h)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        GL11.glPopMatrix()
    }

    fun renderUILines(
        panel: UIPanelAPI,
        alphaMult: Float,
        boxColor: Color = Global.getSettings().darkPlayerColor,
        progression: Color = Global.getSettings().brightPlayerColor,
        widthPadding: Float = 0f,
        renderProgress: Boolean = false,
        progress: Float = 0f
    ) {
        renderUILines(panel.x, panel.y, panel.width, panel.height, alphaMult, boxColor, progression, widthPadding, renderProgress, progress)
    }

    fun renderUILines(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alphaMult: Float,
        boxColor: Color = Global.getSettings().darkPlayerColor,
        progression: Color = Global.getSettings().brightPlayerColor,
        widthPadding: Float = 0f,
        renderProgress: Boolean = false,
        progress: Float = 0f
    ) {
        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)

        if (renderProgress) {
            GL11.glColor4f(
                progression.red / 255f,
                progression.green / 255f,
                progression.blue / 255f,
                alphaMult
            )

            val width = (width + widthPadding) * progress
            val height = height

            drawRectGL(x, y, width, height)
        }

        GL11.glColor4f(
            boxColor.red / 255f,
            boxColor.green / 255f,
            boxColor.blue / 255f,
            alphaMult
        )

        val w = width + widthPadding
        val h = height

        drawRectGL(x, y, w, 1f)
        drawRectGL(x, y + h, w, 1f)

        drawRectGL(x, y, 1f, h)
        drawRectGL(x + w, y, 1f, h)

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glPopMatrix()
    }

    private fun drawRectGL(x: Float, y: Float, w: Float, h: Float) {
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(x, y)
        GL11.glVertex2f(x + w, y)
        GL11.glVertex2f(x + w, y + h)
        GL11.glVertex2f(x, y + h)
        GL11.glEnd()
    }

    fun darkenBackground(
        alphaMult: Float,
        bgColor: Color = Color.BLACK.withAlphaMult(0.6f)
    ) {
        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        val bgAlpha = bgColor.alphaf * alphaMult
        GL11.glColor4f(bgColor.redf, bgColor.greenf, bgColor.bluef, bgAlpha)

        val screenW = Global.getSettings().screenWidth
        val screenH = Global.getSettings().screenHeight

        GL11.glRectf(0f, 0f, screenW, screenH)

        GL11.glDisable(GL11.GL_BLEND)
        GL11.glPopMatrix()
    }

    fun darkenBackgroundAround(alphaMult: Float, panel: UIPanelAPI, bgColor: Color = Color.BLACK.withAlphaMult(0.6f)) {
        darkenBackgroundAround(alphaMult, panel.x, panel.y, panel.width, panel.height, bgColor)
    }

    fun darkenBackgroundAround(
        alphaMult: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        bgColor: Color = Color.BLACK.withAlphaMult(0.6f)
    ) {
        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        val bgAlpha = bgColor.alphaf * alphaMult
        GL11.glColor4f(bgColor.redf, bgColor.greenf, bgColor.bluef, bgAlpha)
        val screenW = Global.getSettings().screenWidth
        val screenH = Global.getSettings().screenHeight
        val buffer = -5f
        val tx = x - buffer
        val ty = y - buffer
        val tw = width + buffer * 2
        val th = height + buffer * 2
        // Left
        GL11.glRectf(0f, 0f, tx, screenH)
        // Right
        GL11.glRectf(tx + tw, 0f, screenW, screenH)
        // Top
        GL11.glRectf(tx, ty + th, tx + tw, screenH)
        // Bottom
        GL11.glRectf(tx, 0f, tx + tw, ty)

        GL11.glDisable(GL11.GL_BLEND)
        GL11.glPopMatrix()
    }

    @JvmOverloads
    fun isMouseHoveringOverComponent(
        component: UIComponentAPI,
        mouseX: Int = Global.getSettings().mouseX,
        mouseY: Int = Global.getSettings().mouseY,
        pad: Float = 0f,
    ): Boolean {
        return isMouseWithinBounds(component.x, component.y, component.width, component.height, pad, mouseX, mouseY)
    }

    fun isMouseHoveringOverComponent(
        component: UIComponentAPI,
        leftPad: Float = 0f,
        topPad: Float = 0f,
        rightPad: Float = 0f,
        bottomPad: Float = 0f,
        mouseX: Int = Global.getSettings().mouseX,
        mouseY: Int = Global.getSettings().mouseY,
    ): Boolean {
        return isMouseWithinBounds(component.x, component.y, component.width, component.height, leftPad, topPad, rightPad, bottomPad, mouseX, mouseY)
    }

    @JvmOverloads
    fun isMouseWithinBounds(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        mouseX: Int = Global.getSettings().mouseX,
        mouseY: Int = Global.getSettings().mouseY,
    ): Boolean {
        return mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height
    }

    fun isMouseWithinBounds(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pad: Float = 0f,
        mouseX: Int = Global.getSettings().mouseX,
        mouseY: Int = Global.getSettings().mouseY,
    ): Boolean {
        return isMouseWithinBounds(x, y, width, height, pad, pad, pad, pad, mouseX, mouseY)
    }

    fun isMouseWithinBounds(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        leftPad: Float = 0f,
        topPad: Float = 0f,
        rightPad: Float = 0f,
        bottomPad: Float = 0f,
        mouseX: Int = Global.getSettings().mouseX,
        mouseY: Int = Global.getSettings().mouseY,
    ): Boolean {
        return mouseX >= x - leftPad && mouseX <= x - leftPad + width + leftPad + rightPad &&
                mouseY >= y - topPad && mouseY <= y - topPad + height + topPad + bottomPad
    }
}