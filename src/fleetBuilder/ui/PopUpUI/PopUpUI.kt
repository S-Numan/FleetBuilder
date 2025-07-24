package fleetBuilder.ui.PopUpUI

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import fleetBuilder.util.FBMisc
import fleetBuilder.util.ReflectionMisc.getCoreUI
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color

//Copied and modified from AshLib

open class PopUpUI : CustomUIPanelPlugin {
    var limit: Int = 5
    var totalFrames: Float = 0f
    var attemptedExit: Boolean = false

    var blackBackground: SpriteAPI = Global.getSettings().getSprite("FleetBuilder", "white_square")
    var borders: SpriteAPI = Global.getSettings().getSprite("FleetBuilder", "white_square")
    var panelBackground: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_center")
    var bot: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_bot")
    var top: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_top")
    var left: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_left")
    var right: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_right")
    var topLeft: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_top_left")
    var topRight: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_top_right")
    var bottomLeft: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_bot_left")
    var bottomRight: SpriteAPI = Global.getSettings().getSprite("ui", "panel00_bot_right")
    var parent: UIPanelAPI? = null
    var frames: Float = 0f
    var panelToInfluence: CustomPanelAPI? = null
    var rendererBorder: UILinesRenderer = UILinesRenderer(0f)
    var isDialog: Boolean = true

    var reachedMaxHeight: Boolean = false
    var originalSizeX: Float = 0f
    var originalSizeY: Float = 0f
    open var x: Float = 0f
    open var y: Float = 0f

    override fun positionChanged(position: PositionAPI?) {
    }

    fun init(panelAPI: CustomPanelAPI, x: Float, y: Float, isDialog: Boolean) {
        panelToInfluence = panelAPI
        parent = getCoreUI()
        originalSizeX = panelAPI.getPosition().getWidth()
        originalSizeY = panelAPI.getPosition().getHeight()

        panelToInfluence!!.getPosition().setSize(16f, 16f)
        this.isDialog = isDialog

        parent!!.addComponent(panelToInfluence).inTL(x, parent!!.getPosition().getHeight() - y)
        parent!!.bringComponentToTop(panelToInfluence)
        rendererBorder.setPanel(panelToInfluence)
    }

    open fun createUI() {
    }

    override fun renderBelow(alphaMult: Float) {
        if (panelToInfluence != null) {
            val renderer = TiledTextureRenderer(panelBackground.getTextureId())
            if (isDialog) {
                blackBackground.setSize(getCoreUI()!!.getPosition().getWidth(), getCoreUI()!!.getPosition().getHeight())
                blackBackground.setColor(Color.black)
                blackBackground.setAlphaMult(0.6f)
                blackBackground.renderAtCenter(getCoreUI()!!.getPosition().getCenterX(), getCoreUI()!!.getPosition().getCenterY())
                renderer.renderTiledTexture(
                    panelToInfluence!!.getPosition().getX(),
                    panelToInfluence!!.getPosition().getY(), panelToInfluence!!.getPosition().getWidth(),
                    panelToInfluence!!.getPosition().getHeight(), panelBackground.getTextureWidth(),
                    panelBackground.getTextureHeight(), (frames / limit) * 0.9f, Color.BLACK
                )
            } else {
                renderer.renderTiledTexture(
                    panelToInfluence!!.getPosition().getX(),
                    panelToInfluence!!.getPosition().getY(), panelToInfluence!!.getPosition().getWidth(),
                    panelToInfluence!!.getPosition().getHeight(), panelBackground.getTextureWidth(),
                    panelBackground.getTextureHeight(), (frames / limit), panelBackground.getColor()
                )
            }
            if (isDialog) {
                renderBorders(panelToInfluence!!)
            } else {
                rendererBorder.render(alphaMult)
            }
        }
    }

    override fun render(alphaMult: Float) {
    }

    override fun advance(amount: Float) {
        if (frames <= limit) {
            frames++
            val progress = frames / limit
            if (frames < limit && !reachedMaxHeight) {
                panelToInfluence!!.getPosition().setSize(originalSizeX, originalSizeY * progress)
                return
            }
            if (frames >= limit && !reachedMaxHeight) {
                reachedMaxHeight = true
                panelToInfluence!!.getPosition().setSize(originalSizeX, originalSizeY)
                createUI()
                return
            }
        }
    }

    open fun applyConfirmScript() {
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        for (event in events) {
            if (frames >= limit - 1 && reachedMaxHeight) {
                if (event.isMouseDownEvent && !isDialog) {
                    val hovers = FBMisc.isMouseHoveringOverComponent(panelToInfluence!!)
                    if (!hovers) {
                        forceDismiss()
                        event.consume()
                    }
                }
                if (!event.isConsumed) {
                    if (event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) {
                        if (attemptedExit) {
                            forceDismiss()
                            event.consume()
                            break
                        } else {
                            attemptedExit = true
                        }
                    }
                }
            }
            event.consume()
        }
    }

    fun forceDismiss() {
        parent!!.removeComponent(panelToInfluence)
        onExit()
    }

    open fun onExit() {
    }

    override fun buttonPressed(buttonId: Any) {
    }

    fun renderBorders(panelAPI: CustomPanelAPI) {
        val leftX = panelAPI.getPosition().getX() + 16
        var currAlpha = frames / limit
        if (currAlpha >= 1) currAlpha = 1f
        top.setSize(16f, 16f)
        bot.setSize(16f, 16f)
        topLeft.setSize(16f, 16f)
        topRight.setSize(16f, 16f)
        bottomLeft.setSize(16f, 16f)
        bottomRight.setSize(16f, 16f)
        left.setSize(16f, 16f)
        right.setSize(16f, 16f)

        top.setAlphaMult(currAlpha)
        bot.setAlphaMult(currAlpha)
        topLeft.setAlphaMult(currAlpha)
        topRight.setAlphaMult(currAlpha)
        bottomLeft.setAlphaMult(currAlpha)
        bottomRight.setAlphaMult(currAlpha)
        left.setAlphaMult(currAlpha)
        right.setAlphaMult(currAlpha)

        val rightX = panelAPI.getPosition().getX() + panelAPI.getPosition().getWidth() - 16
        val botX = panelAPI.getPosition().getY() + 16
        startStencilWithXPad(panelAPI, 8f)
        run {
            var i = leftX
            while (i <= panelAPI.getPosition().getX() + panelAPI.getPosition().getWidth()) {
                top.renderAtCenter(i, panelAPI.getPosition().getY() + panelAPI.getPosition().getHeight())
                bot.renderAtCenter(i, panelAPI.getPosition().getY())
                i += top.getWidth()
            }
        }
        endStencil()
        startStencilWithYPad(panelAPI, 8f)
        var i = botX
        while (i <= panelAPI.getPosition().getY() + panelAPI.getPosition().getHeight()) {
            left.renderAtCenter(panelAPI.getPosition().getX(), i)
            right.renderAtCenter(panelAPI.getPosition().getX() + panelAPI.getPosition().getWidth(), i)
            i += top.getWidth()
        }
        endStencil()
        topLeft.renderAtCenter(leftX - 16, panelAPI.getPosition().getY() + panelAPI.getPosition().getHeight())
        topRight.renderAtCenter(panelAPI.getPosition().getX() + panelAPI.getPosition().getWidth(), panelAPI.getPosition().getY() + panelAPI.getPosition().getHeight())
        bottomLeft.renderAtCenter(leftX - 16, panelAPI.getPosition().getY())
        bottomRight.renderAtCenter(panelAPI.getPosition().getX() + panelAPI.getPosition().getWidth(), panelAPI.getPosition().getY())
    }

    companion object {
        var buttonConfirmWidth: Float = 160f

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
    }
}