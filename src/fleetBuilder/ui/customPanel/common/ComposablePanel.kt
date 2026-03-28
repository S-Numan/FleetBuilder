package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.setSize
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.ui.customPanel.DialogUtils

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
}