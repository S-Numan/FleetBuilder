package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.customPanel.DialogUtil
import fleetBuilder.util.FBMisc.endStencil
import fleetBuilder.util.FBMisc.renderTiledTexture
import fleetBuilder.util.FBMisc.startStencilWithXPad
import fleetBuilder.util.FBMisc.startStencilWithYPad
import fleetBuilder.util.ReflectionMisc
import starficz.centerX
import starficz.centerY
import starficz.height
import starficz.setSize
import starficz.width
import starficz.x
import starficz.y
import java.awt.Color

open class ComposableUIPanel : CustomUIPanel() {
    // Distance the tooltip holds from every side of it's home panel.
    protected open var xTooltipPad = 10f
    open fun getXTooltipPadding(): Float = xTooltipPad
    protected open var yTooltipPad = 10f
    open fun getYTooltipPadding(): Float = yTooltipPad

    override fun createUI() {
        createUICallback?.invoke()
    }

    private var createUICallback: (() -> Unit)? = null
    open fun onCreateUI(
        width: Float = Global.getSettings().screenWidth / 2,
        height: Float = Global.getSettings().screenHeight / 2,
        parent: UIPanelAPI? = null,
        xOffset: Float? = null,
        yOffset: Float? = null,
        callback: (TooltipMakerAPI) -> Unit
    ) {
        // store a callback
        createUICallback = {
            // create the tooltip
            val tooltipWidth = panel.width - (getXTooltipPadding() * 3)
            val tooltipHeight = panel.height - getYTooltipPadding()
            tooltip = panel.createUIElement(tooltipWidth, tooltipHeight, false)
            tooltip.setSize(tooltipWidth, tooltipHeight)
            // run the user code
            callback(tooltip)
            // add UI automatically afterward
            panel.addUIElement(tooltip)
            tooltip.position.inTL(getXTooltipPadding(), getYTooltipPadding())
            panel
        }
        DialogUtil.initDialogToShow(this, x = xOffset, y = yOffset, width = width, height = height, parent = parent)
    }


    override fun init(
        width: Float,
        height: Float,
        xOffset: Float,
        yOffset: Float,
        parent: UIPanelAPI?
    ): CustomPanelAPI {
        super.init(width = width, height = height, xOffset = xOffset, yOffset = yOffset, parent = parent)
        initBorderSprites()
        blackBackground.setSize(Global.getSettings().screenWidth, Global.getSettings().screenHeight)
        blackBackground.color = Color.black
        return panel
    }

    open var dialogStyle: Boolean = false

    open var darkenBackground: Boolean = true
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

    override fun renderBelow(alphaMult: Float) {
        if (darkenBackground) {
            blackBackground.alphaMult = blackBackgroundAlphaMult * alpha
            blackBackground.renderAtCenter(Global.getSettings().screenWidth / 2f, Global.getSettings().screenHeight / 2f)
        }

        if (dialogStyle) {
            renderTiledTexture(
                background.textureId,
                panel.x,
                panel.y, panel.width,
                panel.height, background.textureWidth,
                background.textureHeight, alpha * backgroundAlphaMult, Color.BLACK
            )

            renderBorders()
        } else {
            super.renderBelow(alphaMult)
        }
    }

    private fun initBorderSprites() {
        listOf(top, bot, left, right, topLeft, topRight, bottomLeft, bottomRight)
            .forEach { it.setSize(16f, 16f) }
    }

    fun renderBorders() {
        val leftX = panel.position.x + 16

        top.alphaMult = alpha
        bot.alphaMult = alpha
        topLeft.alphaMult = alpha
        topRight.alphaMult = alpha
        bottomLeft.alphaMult = alpha
        bottomRight.alphaMult = alpha
        left.alphaMult = alpha
        right.alphaMult = alpha

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