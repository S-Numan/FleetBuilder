package fleetBuilder.ui.customPanel.core

import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.lastComponent
import fleetBuilder.otherMods.starficz.setSize
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.ui.customPanel.DialogUtils
import fleetBuilder.util.api.kotlin.safeInvoke

open class ComposablePanel : BasePanel() {

    // Distance the tooltip holds from every side of it's home panel.
    /** The distance the tooltip is created from the left and right side of its parent panel. This modifies the tooltip's size and position */
    open var tooltipPadFromSide = 0f

    /** The distance the tooltip is created from the bottom of the parent panel. This modifies the tooltip's size and position */
    open var tooltipPadFromBottom = 0f

    /** The distance the tooltip is created from the top of the parent panel. This modifies the tooltip's size and position */
    open var tooltipPadFromTop: Float = 0f

    override fun createUI() {
        createUICallback?.invoke()
    }

    /** Removes the tooltip from this panel, then calls createUI again re-creating it. */
    open fun recreateUI() {
        //TODO: Add a boolean set to false by default that automatically saves and re-applies the scroller position.
        val scrollPanel = tooltip?.externalScroller
        if (scrollPanel != null)
            panel.removeComponent(scrollPanel)
        else
            panel.removeComponent(tooltip)

        tooltip = null

        createUI()
    }

    private var createUICallback: (() -> Unit)? = null

    /**
     * Builds the tooltip for this panel. The result of this function is called when the panel is ready to display its tooltip, typically after any animation if present.
     *
     * [present] should be called after this function to start displaying this panel to the user.
     */
    open fun buildUI(
        withScroller: Boolean = false,
        callback: (TooltipMakerAPI) -> Unit
    ) {
        // store a callback
        createUICallback = {
            // create the tooltip
            val tooltipWidth = panel.width - (tooltipPadFromSide * 2)
            val tooltipHeight = panel.height - tooltipPadFromBottom - tooltipPadFromTop

            // create tooltip
            val tooltip = panel.createUIElement(tooltipWidth, tooltipHeight, withScroller)

            // set size again
            tooltip.setSize(tooltipWidth, tooltipHeight)
            // Align new tooltip components to the top left to rid of the mysterious 5f x pad in some components, such as buttons.
            // This will cause mis-positioning for components that rely on it, such as headers. I advise using .position.setXAlignOffset(0f) to re-align them.
            tooltip.addSpacer(0f).position?.inTL(0f, 0f)

            // run the user code
            callback(tooltip)
            // add UI automatically afterward
            panel.addUIElement(tooltip)

            // apply TL. This is done on lastComponent added instead of the tooltip to account for positioning the scroller which may contain the tooltip. In short, avoid positioning the wrong thing.
            panel.lastComponent?.position?.inTL(tooltipPadFromSide, tooltipPadFromTop)
            if (withScroller)

                tooltip.externalScroller?.safeInvoke("setDoNotRenderShadows", true)

            this.tooltip = tooltip
        }
    }

    /**
     * Displays the contents of the panel to the user. Must be called after [buildUI] to do anything, as that is what this function displays.
     *
     * @param width The width of the panel. Defaults to 800.
     * @param height The height of the panel. Defaults to 800.
     * @param parent The parent panel of the panel. Defaults to null. If null, automatically gets the screen panel.
     * @param xOffset The x offset of the panel. Defaults to null. If null, automatically positions this panel to the center of its parent.
     * @param yOffset The y offset of the panel. Defaults to null. If null, automatically positions this panel to the center of its parent.
     */
    @JvmOverloads
    open fun present(
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
     * Builds the contents of the panel and displays it to the user.
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
    open fun show(
        width: Float = 800f,
        height: Float = 800f,
        parent: UIPanelAPI? = null,
        xOffset: Float? = null,
        yOffset: Float? = null,
        withScroller: Boolean = false,
        callback: (TooltipMakerAPI) -> Unit
        //ui: TooltipMakerAPI.() -> Unit
    ) {
        buildUI(withScroller, callback)
        present(width = width, height = height, parent = parent, xOffset = xOffset, yOffset = yOffset)
    }
}