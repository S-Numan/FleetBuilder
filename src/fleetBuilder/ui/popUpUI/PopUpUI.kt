package fleetBuilder.ui.popUpUI

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.util.FBMisc.isMouseWithinBounds
import fleetBuilder.util.PlaceholderDialog
import fleetBuilder.util.ReflectionMisc.getCoreUI
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import starficz.ReflectionUtils.get
import starficz.ReflectionUtils.invoke
import starficz.findChildWithMethod
import starficz.height
import starficz.width
import starficz.x
import starficz.y
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
    lateinit var panel: CustomPanelAPI
    var rendererBorder: UILinesRenderer = UILinesRenderer(0f)
    var isDialog: Boolean = true
    var quitWithEscKey: Boolean = true

    var reachedMaxHeight: Boolean = false
    var originalSizeX: Float = 0f
    var originalSizeY: Float = 0f
    open var x: Float = 0f
    open var y: Float = 0f

    fun init(
        insertPanel: CustomPanelAPI,
        x: Float,
        y: Float,
        parent: UIPanelAPI? = getCoreUI(),
        isDialog: Boolean = true
    ) {
        this.isDialog = isDialog

        panel = insertPanel

        this.parent = parent

        originalSizeX = panel.position.width
        originalSizeY = panel.position.height

        panel.position.setSize(16f, 16f)

        parent!!.addComponent(panel).inTL(x, parent.position.height - y)
        parent.bringComponentToTop(panel)

        rendererBorder.setPanel(panel)

        if (!isDialog) {
            quitWithEscKey = false
            setMaxSize()
        }
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

            val hovers = isMouseWithinBounds(panel.x, panel.y, panel.width, panel.height)

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
        parent!!.removeComponent(panel)
        applyExitScript()
    }

    fun forceDismissNoExit() {
        parent!!.removeComponent(panel)
    }

    private var exitCallback: (() -> Unit)? = null

    open fun applyExitScript() {
        if (placeholderDialog != null)
            placeholderDialog!!.invoke("dismiss", 0)

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

            ui.showMessageDialog("FleetBuilder Placeholder Dialog")
            val screenPanel = ui.get("screenPanel") as? UIPanelAPI
            placeholderDialog = screenPanel?.findChildWithMethod("getOptionMap") as? UIPanelAPI
            if (placeholderDialog != null) {
                placeholderDialog!!.invoke("setOpacity", 0f)
                placeholderDialog!!.invoke("setBackgroundDimAmount", 0f)
                placeholderDialog!!.invoke("setAbsorbOutsideEvents", false)
                placeholderDialog!!.invoke("makeOptionInstant", 0)
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
        val renderer = TiledTextureRenderer(panelBackground.getTextureId())
        if (isDialog) {
            blackBackground.setSize(getCoreUI()!!.getPosition().getWidth(), getCoreUI()!!.getPosition().getHeight())
            blackBackground.setColor(Color.black)
            blackBackground.setAlphaMult(0.6f)
            blackBackground.renderAtCenter(getCoreUI()!!.getPosition().getCenterX(), getCoreUI()!!.getPosition().getCenterY())
            renderer.renderTiledTexture(
                panel.getPosition().getX(),
                panel.getPosition().getY(), panel.getPosition().getWidth(),
                panel.getPosition().getHeight(), panelBackground.getTextureWidth(),
                panelBackground.getTextureHeight(), (frames / limit) * 0.9f, Color.BLACK
            )
        } else {
            renderer.renderTiledTexture(
                panel.getPosition().getX(),
                panel.getPosition().getY(), panel.getPosition().getWidth(),
                panel.getPosition().getHeight(), panelBackground.getTextureWidth(),
                panelBackground.getTextureHeight(), (frames / limit), panelBackground.getColor()
            )
        }
        if (isDialog) {
            renderBorders()
        } else {
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

        top.setAlphaMult(currAlpha)
        bot.setAlphaMult(currAlpha)
        topLeft.setAlphaMult(currAlpha)
        topRight.setAlphaMult(currAlpha)
        bottomLeft.setAlphaMult(currAlpha)
        bottomRight.setAlphaMult(currAlpha)
        left.setAlphaMult(currAlpha)
        right.setAlphaMult(currAlpha)

        val rightX = panel.getPosition().getX() + panel.getPosition().getWidth() - 16
        val botX = panel.getPosition().getY() + 16
        startStencilWithXPad(panel, 8f)
        run {
            var i = leftX
            while (i <= panel.getPosition().getX() + panel.getPosition().getWidth()) {
                top.renderAtCenter(i, panel.getPosition().getY() + panel.getPosition().getHeight())
                bot.renderAtCenter(i, panel.getPosition().getY())
                i += top.getWidth()
            }
        }
        endStencil()
        startStencilWithYPad(panel, 8f)
        var i = botX
        while (i <= panel.getPosition().getY() + panel.getPosition().getHeight()) {
            left.renderAtCenter(panel.getPosition().getX(), i)
            right.renderAtCenter(panel.getPosition().getX() + panel.getPosition().getWidth(), i)
            i += top.getWidth()
        }
        endStencil()
        topLeft.renderAtCenter(leftX - 16, panel.getPosition().getY() + panel.getPosition().getHeight())
        topRight.renderAtCenter(panel.getPosition().getX() + panel.getPosition().getWidth(), panel.getPosition().getY() + panel.getPosition().getHeight())
        bottomLeft.renderAtCenter(leftX - 16, panel.getPosition().getY())
        bottomRight.renderAtCenter(panel.getPosition().getX() + panel.getPosition().getWidth(), panel.getPosition().getY())
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
    }
}