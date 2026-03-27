package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.FBMisc.endStencil
import fleetBuilder.util.FBMisc.renderTiledTexture
import fleetBuilder.util.FBMisc.startStencilWithXPad
import fleetBuilder.util.FBMisc.startStencilWithYPad
import java.awt.Color

open class ComposablePanel : BasePanel() {

    // Distance the tooltip holds from every side of it's home panel.
    open var tooltipPadFromSide = 0f
    open var tooltipPadFromBottom = 0f
    open var tooltipPadFromTop: Float = 0f

    override fun createUI() {
        createUICallback?.invoke()
    }

    open fun recreateUI() {
        panel.removeComponent(tooltip)
        createUI()
    }

    private var createUICallback: (() -> Unit)? = null

    /**
     * Builds the UI for this panel. Occurs after open animation finish, if animation is present.
     */
    open fun buildUI(
        callback: (TooltipMakerAPI) -> Unit
    ) {
        // store a callback
        createUICallback = {
            // create the tooltip
            val tooltipWidth = panel.width - (tooltipPadFromSide * 2)
            val tooltipHeight = panel.height - tooltipPadFromBottom - tooltipPadFromTop
            tooltip = panel.createUIElement(tooltipWidth, tooltipHeight, false)
            tooltip!!.setSize(tooltipWidth, tooltipHeight)

            // Align new tooltip components to the top left to rid of the mysterious 5f x pad
            tooltip!!.addSpacer(0f).position?.inTL(0f, 0f)

            // run the user code
            callback(tooltip!!)
            // add UI automatically afterward
            panel.addUIElement(tooltip)
            tooltip!!.position.inTL(tooltipPadFromSide, tooltipPadFromTop)
        }
    }

    /**
     * Shows this panel. Must be called after [buildUI] to do anything, as that is what this function shows.
     *
     * @param width The width of the panel. Defaults to 800.
     * @param height The height of the panel. Defaults to 800.
     * @param parent The parent panel of the panel. Defaults to null. If null, automatically gets the screen panel.
     * @param xOffset The x offset of the panel. Defaults to null. If null, automatically positions this panel to the center of its parent.
     * @param yOffset The y offset of the panel. Defaults to null. If null, automatically positions this panel to the center of its parent.
     */
    @JvmOverloads
    fun show(
        width: Float = 800f,
        height: Float = 800f,
        parent: UIPanelAPI? = null,
        xOffset: Float? = null,
        yOffset: Float? = null
    ) {
        // Calls init, but in a fancy manner to allow caching the tooltip and making it later in case it's too early for the game to show a panel.
        DialogUtils.initDialogToShow(
            this,
            width = width,
            height = height,
            parent = parent,
            xOffset = xOffset,
            yOffset = yOffset,
        )
    }

    /**
     * Builds the panel UI and shows it as a panel.
     *
     * The [callback] is invoked when the panel is ready to create its UI,
     * with a [TooltipMakerAPI] for adding elements. If this panel has an opening animation, this will call when the animation is over rather than right away.
     *
     * @param width The width of the panel. Defaults to 800.
     * @param height The height of the panel. Defaults to 800.
     * @param parent The parent panel of the panel. Defaults to null. If null, automatically gets the screen panel.
     * @param xOffset The x offset of the panel. Defaults to null. If null, automatically positions this panel to the center of its parent.
     * @param yOffset The y offset of the panel. Defaults to null. If null, automatically positions this panel to the center of its parent.
     * @param callback Lambda used to build the panel UI.
     */
    @JvmOverloads
    fun show(
        width: Float = 800f,
        height: Float = 800f,
        parent: UIPanelAPI? = null,
        xOffset: Float? = null,
        yOffset: Float? = null,
        callback: (TooltipMakerAPI) -> Unit
        //ui: TooltipMakerAPI.() -> Unit
    ) {
        buildUI(callback)
        show(width = width, height = height, parent = parent, xOffset = xOffset, yOffset = yOffset)
    }

    open var dialogStyle: Boolean = false

    open var darkenBackground: Boolean = false
    open var darkenBackgroundAlphaMult: Float = 0.6f

    protected fun borderSprite(id: String) =
        sprite("ui", id).apply { setSize(16f, 16f) }

    open val bot = borderSprite("panel00_bot")
    open val top = borderSprite("panel00_top")
    open val left = borderSprite("panel00_left")
    open val right = borderSprite("panel00_right")
    open val topLeft = borderSprite("panel00_top_left")
    open val topRight = borderSprite("panel00_top_right")
    open val bottomLeft = borderSprite("panel00_bot_left")
    open val bottomRight = borderSprite("panel00_bot_right")

    override fun renderBelow(alphaMult: Float) {
        if (darkenBackground)
            UIUtils.darkenBackground(alphaMult * (alpha * darkenBackgroundAlphaMult), Color.BLACK)

        if (dialogStyle) {
            renderTiledTexture(
                background.textureId,
                panel.x,
                panel.y, panel.width,
                panel.height, background.textureWidth,
                background.textureHeight, alpha * background.alphaMult, background.color
            )

            if (renderUIBorders)
                renderBorders()
        } else {
            super.renderBelow(alphaMult)
        }
    }

    fun renderBorders() {
        val leftX = panel.position.x + 16

        listOf(top, bot, left, right, topLeft, topRight, bottomLeft, bottomRight)
            .forEach {
                it.alphaMult = alpha
                if (uiBorderColor != null)
                    it.color = uiBorderColor
            }

        //val rightX = panel.getPosition().getX() + panel.getPosition().getWidth() - 16
        val botX = panel.y + 16
        startStencilWithXPad(panel, 8f)

        var i = leftX
        while (i <= panel.x + panel.width) {
            top.renderAtCenter(i, panel.y + panel.height)
            bot.renderAtCenter(i, panel.y)
            i += top.width
        }

        endStencil()
        startStencilWithYPad(panel, 8f)
        var q = botX
        while (q <= panel.y + panel.height) {
            left.renderAtCenter(panel.x, q)
            right.renderAtCenter(panel.x + panel.width, q)
            q += top.width
        }
        endStencil()
        topLeft.renderAtCenter(leftX - 16, panel.y + panel.height)
        topRight.renderAtCenter(panel.x + panel.width, panel.y + panel.height)
        bottomLeft.renderAtCenter(leftX - 16, panel.y)
        bottomRight.renderAtCenter(panel.x + panel.width, panel.y)
    }
}