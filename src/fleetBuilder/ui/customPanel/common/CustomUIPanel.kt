package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.displayMessages.DisplayMessages
import fleetBuilder.util.FBMisc
import fleetBuilder.util.UIUtils
import org.lwjgl.opengl.GL11
import starficz.*
import java.awt.Color

//Copied and heavily modified from AshLib

open class CustomUIPanel : CustomUIPanelPlugin {
    open var x: Float = 0f
    open var y: Float = 0f

    open lateinit var parent: UIPanelAPI
    open lateinit var panel: CustomPanelAPI

    open var consumeInnerMouseInputs: Boolean = true

    open var createUIOnInit: Boolean = true


    open var currAlpha: Float = 1f
    open val rendererBorder: UILinesRenderer = UILinesRenderer(0f)

    private val settings = Global.getSettings()

    protected fun sprite(cat: String, id: String): SpriteAPI =
        settings.getSprite(cat, id)

    open val panelBackground = sprite("ui", "panel00_center")
    open var panelBackgroundAlphaMult: Float = 1f

    open fun init(
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        parent: UIPanelAPI?
    ): CustomPanelAPI {
        panel = Global.getSettings().createCustom(width, height, this)

        this.parent = parent ?: run {
            DisplayMessages.showError("parent was null when creating dialog")
            return panel
        }

        parent.addComponent(panel).inTL(x, parent.position.height - y)
        parent.bringComponentToTop(panel)

        rendererBorder.setPanel(panel)

        if (createUIOnInit)
            createUI()

        return panel
    }

    open fun createUI() {
    }

    override fun advance(amount: Float) {
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        for (event in events) {
            if (event.isConsumed) continue

            val hovers = UIUtils.isMouseWithinBounds(panel.x, panel.y, panel.width, panel.height)

            if (hovers && consumeInnerMouseInputs && event.isMouseEvent)
                event.consume()
        }
    }

    open fun forceDismiss() {
        parent.removeComponent(panel)
        applyExitScript()
    }

    open fun forceDismissNoExit() {
        parent.removeComponent(panel)
    }

    private var exitCallback: (() -> Unit)? = null

    open fun applyExitScript() {
        exitCallback?.invoke()
    }

    fun onExit(callback: () -> Unit) {
        exitCallback = callback
    }

    override fun positionChanged(position: PositionAPI?) {
    }

    override fun buttonPressed(buttonId: Any?) {
    }

    override fun render(alphaMult: Float) {
    }

    override fun renderBelow(alphaMult: Float) {
        renderTiledTexture(
            panelBackground.textureId,
            panel.x,
            panel.y, panel.width,
            panel.height, panelBackground.textureWidth,
            panelBackground.textureHeight, currAlpha * panelBackgroundAlphaMult, panelBackground.color
        )

        rendererBorder.render(alphaMult)
    }

    companion object {
        fun startStencilWithYPad(panel: CustomPanelAPI, yPad: Float) {
            GL11.glClearStencil(0)
            GL11.glStencilMask(0xff)
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

            GL11.glColorMask(false, false, false, false)
            GL11.glEnable(GL11.GL_STENCIL_TEST)

            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff)
            GL11.glStencilMask(0xff)
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

            GL11.glBegin(GL11.GL_POLYGON)
            val position = panel.getPosition()
            val x = position.getX() - 5
            val y = position.getY()
            val width = position.getWidth() + 10
            val height = position.getHeight()

            // Define the rectangle
            GL11.glVertex2f(x, y)
            GL11.glVertex2f(x + width, y)
            GL11.glVertex2f(x + width, y + height - yPad)
            GL11.glVertex2f(x, y + height - yPad)
            GL11.glEnd()

            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
            GL11.glColorMask(true, true, true, true)

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
        }

        fun startStencilWithXPad(panel: CustomPanelAPI, xPad: Float) {
            GL11.glClearStencil(0)
            GL11.glStencilMask(0xff)
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

            GL11.glColorMask(false, false, false, false)
            GL11.glEnable(GL11.GL_STENCIL_TEST)

            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xff)
            GL11.glStencilMask(0xff)
            GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)

            GL11.glBegin(GL11.GL_POLYGON)
            val position = panel.getPosition()
            val x = position.getX() - 5
            val y = position.getY() - 10
            val width = position.getWidth() + 10
            val height = position.getHeight() + 20

            // Define the rectangle
            GL11.glVertex2f(x, y)
            GL11.glVertex2f(x + width - xPad, y)
            GL11.glVertex2f(x + width - xPad, y + height)
            GL11.glVertex2f(x, y + height)
            GL11.glEnd()

            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
            GL11.glColorMask(true, true, true, true)

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
        }

        fun endStencil() {
            GL11.glDisable(GL11.GL_STENCIL_TEST)
        }

        fun renderTiledTexture(
            textureId: Int,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            tileWidth: Float,
            tileHeight: Float,
            alphaMult: Float,
            color: Color
        ) {
            if (textureId == 0) {
                DisplayMessages.showError("Error: Invalid texture ID.")
                return
            }

            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)

            // Enable blending for alpha transparency
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            // Set the texture to repeat
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT)

            // Set nearest neighbor filtering to preserve the lines
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)

            // Calculate texture repeat factors based on the panel's size and the texture's tile size
            val uMax: Float = width / tileWidth // Repeat in the X direction
            val vMax: Float = height / tileHeight // Repeat in the Y direction

            // Set color with alpha transparency
            GL11.glColor4f(color.red.toFloat(), color.green.toFloat(), color.blue.toFloat(), alphaMult)

            // Render the panel with tiling
            GL11.glBegin(GL11.GL_QUADS)
            GL11.glTexCoord2f(0f, 0f)
            GL11.glVertex2f(x, y) // Bottom-left
            GL11.glTexCoord2f(uMax, 0f)
            GL11.glVertex2f(x + width, y) // Bottom-right
            GL11.glTexCoord2f(uMax, vMax)
            GL11.glVertex2f(x + width, y + height) // Top-right
            GL11.glTexCoord2f(0f, vMax)
            GL11.glVertex2f(x, y + height) // Top-left
            GL11.glEnd()

            // Reset color to fully opaque to avoid affecting other renders
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f)

            // Disable blending and textures
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
        }
    }
}