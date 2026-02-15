package fleetBuilder.ui.popup.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.displayMessages.DisplayMessages
import fleetBuilder.util.FBMisc
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.safeInvoke
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import starficz.ReflectionUtils.get
import starficz.centerX
import starficz.centerY
import starficz.findChildWithMethod
import starficz.height
import starficz.width
import starficz.x
import starficz.y
import java.awt.Color

//Copied and heavily modified from AshLib

open class PopUpUI : CustomUIPanelPlugin {
    open val limit: Int = 5

    open var frames: Float = 0f
    open var attemptedExit: Boolean = false
    open var reachedMaxHeight: Boolean = false
    open var originalSizeX: Float = 0f
    open var originalSizeY: Float = 0f
    open var x: Float = 0f
    open var y: Float = 0f

    open lateinit var parent: UIPanelAPI
    open lateinit var panel: CustomPanelAPI
    open var isDialog: Boolean = true
    open var quitWithEscKey: Boolean = true

    open val rendererBorder: UILinesRenderer = UILinesRenderer(0f)

    private val settings = Global.getSettings()

    private fun sprite(cat: String, id: String) =
        settings.getSprite(cat, id)

    open val blackBackground = sprite("FleetBuilder", "white_square")
    open val panelBackground = sprite("ui", "panel00_center")
    open val bot = sprite("ui", "panel00_bot")
    open val top = sprite("ui", "panel00_top")
    open val left = sprite("ui", "panel00_left")
    open val right = sprite("ui", "panel00_right")
    open val topLeft = sprite("ui", "panel00_top_left")
    open val topRight = sprite("ui", "panel00_top_right")
    open val bottomLeft = sprite("ui", "panel00_bot_left")
    open val bottomRight = sprite("ui", "panel00_bot_right")

    fun init(
        insertPanel: CustomPanelAPI,
        x: Float,
        y: Float,
        parent: UIPanelAPI? = ReflectionMisc.getCoreUI(true)
    ) {
        panel = insertPanel

        this.parent = parent ?: run {
            DisplayMessages.showError("parent was null when creating dialog")
            return
        }

        originalSizeX = panel.position.width
        originalSizeY = panel.position.height

        panel.position.setSize(16f, 16f)

        parent.addComponent(panel).inTL(x, parent.position.height - y)
        parent.bringComponentToTop(panel)

        rendererBorder.setPanel(panel)

        if (!isDialog) {
            quitWithEscKey = false
            setMaxSize()
        }

        if (Global.getSector()?.campaignUI != null)
            makeDummyDialog(Global.getSector().campaignUI)
    }

    open fun createUI() {
    }

    override fun advance(amount: Float) {
        if (frames <= limit) {
            frames++
            val progress = frames / limit
            if (frames < limit && !reachedMaxHeight) {
                panel.position.setSize(originalSizeX, originalSizeY * progress)
                return
            }
            if (frames >= limit && !reachedMaxHeight) {
                setMaxSize()
                return
            }
        }
    }

    fun setMaxSize() {
        reachedMaxHeight = true
        panel.position.setSize(originalSizeX, originalSizeY)
        createUI()
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        for (event in events) {
            if (event.isConsumed) continue

            val hovers = FBMisc.isMouseWithinBounds(panel.x, panel.y, panel.width, panel.height)

            if (!isDialog) {
                if (hovers)
                    event.consume()
                continue
            }

            if (frames >= limit - 1 && reachedMaxHeight) {
                /*if (event.isMouseDownEvent && !isDialog) {
                    val hovers = FBMisc.isMouseHoveringOverComponent(panelToInfluence!!)
                    if (!hovers) {
                        forceDismiss()
                        event.consume()
                    }
                }*/

                if (quitWithEscKey && event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) {
                    if (attemptedExit) {
                        forceDismiss()
                        event.consume()
                        break
                    } else if (event.isKeyDownEvent) {
                        event.consume()
                        attemptedExit = true
                    } else
                        event.consume()
                }
            }
            event.consume()
        }
    }

    fun forceDismiss() {
        parent.removeComponent(panel)
        applyExitScript()
    }

    fun forceDismissNoExit() {
        parent.removeComponent(panel)

        placeholderDialog?.safeInvoke("dismiss", 0)
    }

    private var exitCallback: (() -> Unit)? = null

    open fun applyExitScript() {
        placeholderDialog?.safeInvoke("dismiss", 0)

        exitCallback?.invoke()
    }

    fun onExit(callback: () -> Unit) {
        exitCallback = callback
    }

    var placeholderDialog: UIPanelAPI? = null
    fun makeDummyDialog(ui: CampaignUIAPI) {
        //Open a dialog to prevent input from most other mods
        if (Global.getSettings().isInCampaignState && !ui.isShowingDialog) {
            //ui.showInteractionDialog(PlaceholderDialog(), Global.getSector().playerFleet) // While this also works, it hides the campaign UI.
            //placeholderDialog = ui.currentInteractionDialog

            ui.showMessageDialog(" ")
            val screenPanel = ui.get("screenPanel") as? UIPanelAPI
            placeholderDialog = screenPanel?.findChildWithMethod("getOptionMap") as? UIPanelAPI
            if (placeholderDialog != null) {
                placeholderDialog!!.safeInvoke("setOpacity", 0f)
                placeholderDialog!!.safeInvoke("setBackgroundDimAmount", 0f)
                placeholderDialog!!.safeInvoke("setAbsorbOutsideEvents", false)
                placeholderDialog!!.safeInvoke("makeOptionInstant", 0)
            }
        }

    }

    override fun positionChanged(position: PositionAPI?) {
    }

    override fun buttonPressed(buttonId: Any?) {
    }

    override fun render(alphaMult: Float) {
    }

    override fun renderBelow(alphaMult: Float) {
        if (isDialog) {
            blackBackground.setSize(ReflectionMisc.getCoreUI(true)!!.width, ReflectionMisc.getCoreUI()!!.height)
            blackBackground.color = Color.black
            blackBackground.alphaMult = 0.6f
            blackBackground.renderAtCenter(ReflectionMisc.getCoreUI(true)!!.centerX, ReflectionMisc.getCoreUI()!!.centerY)
            renderTiledTexture(
                panelBackground.textureId,
                panel.x,
                panel.y, panel.width,
                panel.height, panelBackground.textureWidth,
                panelBackground.textureHeight, (frames / limit) * 0.9f, Color.BLACK
            )

            renderBorders()
        } else {
            renderTiledTexture(
                panelBackground.textureId,
                panel.x,
                panel.y, panel.width,
                panel.height, panelBackground.textureWidth,
                panelBackground.textureHeight, (frames / limit), panelBackground.color
            )

            rendererBorder.render(alphaMult)
        }
    }

    fun renderBorders() {
        val leftX = panel.position.x + 16
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

        top.alphaMult = currAlpha
        bot.alphaMult = currAlpha
        topLeft.alphaMult = currAlpha
        topRight.alphaMult = currAlpha
        bottomLeft.alphaMult = currAlpha
        bottomRight.alphaMult = currAlpha
        left.alphaMult = currAlpha
        right.alphaMult = currAlpha

        //val rightX = panel.getPosition().getX() + panel.getPosition().getWidth() - 16
        val botX = panel.y + 16
        startStencilWithXPad(panel, 8f)
        run {
            var i = leftX
            while (i <= panel.x + panel.width) {
                top.renderAtCenter(i, panel.y + panel.height)
                bot.renderAtCenter(i, panel.y)
                i += top.width
            }
        }
        endStencil()
        startStencilWithYPad(panel, 8f)
        var i = botX
        while (i <= panel.y + panel.height) {
            left.renderAtCenter(panel.x, i)
            right.renderAtCenter(panel.x + panel.width, i)
            i += top.width
        }
        endStencil()
        topLeft.renderAtCenter(leftX - 16, panel.y + panel.height)
        topRight.renderAtCenter(panel.x + panel.width, panel.y + panel.height)
        bottomLeft.renderAtCenter(leftX - 16, panel.y)
        bottomRight.renderAtCenter(panel.x + panel.width, panel.y)
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