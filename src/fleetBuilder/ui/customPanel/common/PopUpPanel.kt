package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.safeInvoke
import org.lwjgl.input.Keyboard
import starficz.ReflectionUtils.get
import starficz.centerX
import starficz.centerY
import starficz.findChildWithMethod
import starficz.height
import starficz.width
import starficz.x
import starficz.y
import java.awt.Color

open class PopUpPanel : CustomUIPanel() {
    override var createUIOnInit = false

    open var originalSizeX: Float = 0f // Unset before init
    open var originalSizeY: Float = 0f // Unset before init
    open val framesToOpen: Int = 5
    open var currentFrame: Float = 0f

    open var quitWithEscKey: Boolean = true

    open var attemptedExit: Boolean = false
    open var reachedMaxHeight: Boolean = false

    open var darkenBackground: Boolean = true

    open var dialogRendering: Boolean = true

    override var panelBackgroundAlphaMult: Float = 0.9f

    open val blackBackground = sprite("FleetBuilder", "white_square")
    open var blackBackgroundAlphaMult: Float = 0.6f
    open val bot = sprite("ui", "panel00_bot")
    open val top = sprite("ui", "panel00_top")
    open val left = sprite("ui", "panel00_left")
    open val right = sprite("ui", "panel00_right")
    open val topLeft = sprite("ui", "panel00_top_left")
    open val topRight = sprite("ui", "panel00_top_right")
    open val bottomLeft = sprite("ui", "panel00_bot_left")
    open val bottomRight = sprite("ui", "panel00_bot_right")

    fun init(width: Float, height: Float, x: Float, y: Float): CustomPanelAPI {
        return init(width, height, x, y, ReflectionMisc.getScreenPanel())
    }

    override fun init(
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        parent: UIPanelAPI?
    ): CustomPanelAPI {
        originalSizeX = width
        originalSizeY = height
        super.init(width, height, x, y, parent)

        if (Global.getSector()?.campaignUI != null)
            makeDummyDialog(Global.getSector().campaignUI)

        return panel
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

    override fun forceDismissNoExit() {
        super.forceDismissNoExit()

        placeholderDialog?.safeInvoke("dismiss", 0)
    }

    override fun applyExitScript() {
        placeholderDialog?.safeInvoke("dismiss", 0)

        super.applyExitScript()
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        if (currentFrame <= framesToOpen) {
            currentFrame++
            val progress = currentFrame / framesToOpen
            if (currentFrame < framesToOpen && !reachedMaxHeight) {
                panel.position.setSize(originalSizeX, originalSizeY * progress)
                currAlpha = currentFrame / framesToOpen
            } else if (currentFrame >= framesToOpen && !reachedMaxHeight) {
                setMaxSize()
            }
        }
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        super.processInput(events)

        for (event in events) {
            if (event.isConsumed) continue

            if (currentFrame >= framesToOpen - 1 && reachedMaxHeight) {
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
                        attemptedExit = true
                    }
                }
            }
            event.consume()
        }
    }

    fun setMaxSize() {
        reachedMaxHeight = true
        panel.position.setSize(originalSizeX, originalSizeY)
        currAlpha = 1f
        createUI()
    }

    override fun renderBelow(alphaMult: Float) {
        if (darkenBackground) {
            val screenPanel = ReflectionMisc.getScreenPanel() ?: return
            blackBackground.setSize(screenPanel.width, screenPanel.height)
            blackBackground.color = Color.black
            blackBackground.alphaMult = blackBackgroundAlphaMult
            blackBackground.renderAtCenter(ReflectionMisc.getScreenPanel()!!.centerX, ReflectionMisc.getScreenPanel()!!.centerY)
        }
        if (dialogRendering) {
            renderTiledTexture(
                panelBackground.textureId,
                panel.x,
                panel.y, panel.width,
                panel.height, panelBackground.textureWidth,
                panelBackground.textureHeight, currAlpha * panelBackgroundAlphaMult, Color.BLACK
            )

            renderBorders()
        } else {
            super.renderBelow(alphaMult)
        }
    }

    fun renderBorders() {
        val leftX = panel.position.x + 16
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
}