package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.FBMisc.endStencil
import fleetBuilder.util.FBMisc.renderTiledTexture
import fleetBuilder.util.FBMisc.startStencilWithXPad
import fleetBuilder.util.FBMisc.startStencilWithYPad
import starficz.*
import java.awt.Color

open class ComposablePanel : BasePanel() {

    // Distance the tooltip holds from every side of it's home panel.
    protected open var xTooltipPad = 0f
    open fun getXTooltipPadding(): Float = xTooltipPad
    protected open var yTooltipPad = 0f
    open fun getYTooltipPadding(): Float = yTooltipPad

    override fun createUI() {
        createUICallback?.invoke()
    }

    private var createUICallback: (() -> Unit)? = null

    /**
     * Create a UI panel with a callback. The callback is responsible for populating the panel with UI elements.
     * This is a more advanced method of creating a UI panel and is recommended for more complex tooltips.
     *
     * The callback will be executed once the panel is ready to be populated with UI elements. If the panel
     * opens with an animation, the callback will be invoked after the animation has finished.
     *
     * @param width The width of the panel.
     * @param height The height of the panel.
     * @param parent The parent panel. Defaults to screenPanel.
     * @param xOffset The x offset from the parent. Defaults to a value which would center this panel within the parent.
     * @param yOffset The y offset from the parent. Defaults to a value which would center this panel within the parent.
     * @param callback The callback used to populate the panel with UI elements. This is executed when the panel
     * is fully ready
     */
    @JvmOverloads
    open fun onCreateUI(
        width: Float = 800f,
        height: Float = 800f,
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
            tooltip!!.setSize(tooltipWidth, tooltipHeight)
            // run the user code
            callback(tooltip!!)
            // add UI automatically afterward
            panel.addUIElement(tooltip)
            tooltip!!.position.inTL(getXTooltipPadding(), getYTooltipPadding())
        }

        // Calls init, but in a fancy manner to allow caching the tooltip and making it later in case it's too early for the game to show a panel.
        DialogUtils.initDialogToShow(this, xOffset = xOffset, yOffset = yOffset, width = width, height = height, parent = parent)
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
        return panel
    }

    open var dialogStyle: Boolean = false

    open var darkenBackground: Boolean = false
    open var darkenBackgroundAlphaMult: Float = 0.6f

    open val bot = sprite("ui", "panel00_bot")
    open val top = sprite("ui", "panel00_top")
    open val left = sprite("ui", "panel00_left")
    open val right = sprite("ui", "panel00_right")
    open val topLeft = sprite("ui", "panel00_top_left")
    open val topRight = sprite("ui", "panel00_top_right")
    open val bottomLeft = sprite("ui", "panel00_bot_left")
    open val bottomRight = sprite("ui", "panel00_bot_right")

    override fun renderBelow(alphaMult: Float) {
        if (darkenBackground)
            UIUtils.darkenBackground(alphaMult * (alpha * darkenBackgroundAlphaMult), Color.BLACK)

        if (dialogStyle) {
            renderTiledTexture(
                background.textureId,
                panel.x,
                panel.y, panel.width,
                panel.height, background.textureWidth,
                background.textureHeight, alpha * background.alphaMult, Color.BLACK
            )

            if (renderUIBorders)
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