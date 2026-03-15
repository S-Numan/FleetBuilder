package starficz.internal

import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import starficz.*
import kotlin.math.abs
import kotlin.math.max

internal interface LayoutStrategy {
    // Anchors the very first element in the layout
    fun anchorFirst(
        component: UIComponentAPI,
        alignment: Alignment,
        primaryMargin: Float,
        secondaryMargin: Float
    ): Float

    // Positions a subsequent element relative to the previous one
    fun positionNext(component: UIComponentAPI, previousElement: UIComponentAPI, alignment: Alignment, spacing: Float)

    // Gets the size of the component along the layout's main direction (e.g., height for vertical)
    fun getPrimaryAxisDimension(component: UIComponentAPI): Float

    // Gets the size of the component perpendicular to the layout's main direction (e.g., width for vertical)
    fun getSecondaryAxisDimension(component: UIComponentAPI): Float

    // Gets the coordinate of the relevant edge for size calculation (e.g., bottom for V-Top, right for H-Left)
    fun getEndEdgeCoordinate(component: UIComponentAPI, alignment: Alignment): Float

    // Sets the final size of the layout panel
    fun setPanelSize(panel: UIPanelAPI, primaryTotalSize: Float, secondaryMaxSize: Float)

    // Determines if the alignment is 'start' aligned for the primary axis (Top for V, Left for H)
    fun isPrimaryAxisStartAligned(alignment: Alignment): Boolean

    // Determines if the alignment is 'end' aligned for the primary axis (Bottom for V, Right for H)
    fun isPrimaryAxisEndAligned(alignment: Alignment): Boolean
}

internal object VerticalStrategy : LayoutStrategy {
    override fun isPrimaryAxisStartAligned(alignment: Alignment) = alignment == Alignment.TL || alignment == Alignment.TMID || alignment == Alignment.TR
    override fun isPrimaryAxisEndAligned(alignment: Alignment) = alignment == Alignment.BL || alignment == Alignment.BMID || alignment == Alignment.BR

    override fun anchorFirst(
        component: UIComponentAPI,
        alignment: Alignment,
        primaryMargin: Float,
        secondaryMargin: Float
    ): Float {
        // primaryMargin = yMargin, secondaryMargin = xMargin for Vertical
        return when (alignment) {
            Alignment.TL -> {
                component.position.inTL(secondaryMargin, primaryMargin); component.top
            }
            Alignment.TMID -> {
                component.position.inTMid(primaryMargin); component.top
            }
            Alignment.TR -> {
                component.position.inTR(secondaryMargin, primaryMargin); component.top
            }
            Alignment.BL -> {
                component.position.inBL(secondaryMargin, primaryMargin); component.bottom
            }
            Alignment.BMID -> {
                component.position.inBMid(primaryMargin); component.bottom
            }
            Alignment.BR -> {
                component.position.inBR(secondaryMargin, primaryMargin); component.bottom
            }
            else -> throw IllegalArgumentException("Alignment $alignment not valid for Vertical Stack layout.")
        }
    }

    override fun positionNext(
        component: UIComponentAPI,
        previousElement: UIComponentAPI,
        alignment: Alignment,
        spacing: Float
    ) {
        when (alignment) {
            Alignment.TL -> component.position.belowLeft(previousElement, spacing)
            Alignment.TMID -> component.position.belowMid(previousElement, spacing)
            Alignment.TR -> component.position.belowRight(previousElement, spacing)
            Alignment.BL -> component.position.aboveLeft(previousElement, spacing)
            Alignment.BMID -> component.position.aboveMid(previousElement, spacing)
            Alignment.BR -> component.position.aboveRight(previousElement, spacing)
            else -> throw IllegalArgumentException("Alignment $alignment not valid for Vertical Stack layout.")
        }
    }

    override fun getPrimaryAxisDimension(component: UIComponentAPI): Float = component.height
    override fun getSecondaryAxisDimension(component: UIComponentAPI): Float = component.width

    override fun getEndEdgeCoordinate(component: UIComponentAPI, alignment: Alignment): Float {
        return if (isPrimaryAxisStartAligned(alignment)) component.bottom // Stacks down
        else if (isPrimaryAxisEndAligned(alignment)) component.top // Stacks up
        else throw IllegalArgumentException("Alignment $alignment not valid for Vertical Stack layout.")
    }

    override fun setPanelSize(panel: UIPanelAPI, primaryTotalSize: Float, secondaryMaxSize: Float) {
        panel.setSize(secondaryMaxSize, primaryTotalSize) // width, height
    }
}

internal object HorizontalStrategy : LayoutStrategy {
    override fun isPrimaryAxisStartAligned(alignment: Alignment) = alignment == Alignment.TL || alignment == Alignment.LMID || alignment == Alignment.BL
    override fun isPrimaryAxisEndAligned(alignment: Alignment) = alignment == Alignment.TR || alignment == Alignment.RMID || alignment == Alignment.BR

    override fun anchorFirst(
        component: UIComponentAPI,
        alignment: Alignment,
        primaryMargin: Float,
        secondaryMargin: Float
    ): Float {
        // primaryMargin = xMargin, secondaryMargin = yMargin for Horizontal
        return when (alignment) {
            Alignment.TL -> {
                component.position.inTL(primaryMargin, secondaryMargin); component.left
            }
            Alignment.LMID -> {
                component.position.inLMid(primaryMargin); component.left
            }
            Alignment.BL -> {
                component.position.inBL(primaryMargin, secondaryMargin); component.left
            }
            Alignment.TR -> {
                component.position.inTR(primaryMargin, secondaryMargin); component.right
            }
            Alignment.RMID -> {
                component.position.inRMid(primaryMargin); component.right
            }
            Alignment.BR -> {
                component.position.inBR(primaryMargin, secondaryMargin); component.right
            }
            else -> throw IllegalArgumentException("Alignment $alignment not valid for Horizontal Stack layout.")
        }
    }

    override fun positionNext(
        component: UIComponentAPI,
        previousElement: UIComponentAPI,
        alignment: Alignment,
        spacing: Float
    ) {
        when (alignment) {
            Alignment.TL -> component.position.rightOfTop(previousElement, spacing)
            Alignment.LMID -> component.position.rightOfMid(previousElement, spacing)
            Alignment.BL -> component.position.rightOfBottom(previousElement, spacing)
            Alignment.TR -> component.position.leftOfTop(previousElement, spacing)
            Alignment.RMID -> component.position.leftOfMid(previousElement, spacing)
            Alignment.BR -> component.position.leftOfBottom(previousElement, spacing)
            else -> throw IllegalArgumentException("Alignment $alignment not valid for Horizontal Stack layout.")
        }
    }

    override fun getPrimaryAxisDimension(component: UIComponentAPI): Float = component.width
    override fun getSecondaryAxisDimension(component: UIComponentAPI): Float = component.height

    override fun getEndEdgeCoordinate(component: UIComponentAPI, alignment: Alignment): Float {
        return if (isPrimaryAxisStartAligned(alignment)) component.right // Stacks right
        else if (isPrimaryAxisEndAligned(alignment)) component.left // Stacks left
        else throw IllegalArgumentException("Alignment $alignment not valid for Horizontal Stack layout.")
    }

    override fun setPanelSize(panel: UIPanelAPI, primaryTotalSize: Float, secondaryMaxSize: Float) {
        panel.setSize(primaryTotalSize, secondaryMaxSize) // width, height
    }
}

internal fun UIPanelAPI.StackLayout(
    strategy: LayoutStrategy,
    anchor: Anchor.AnchorData,
    alignment: Alignment,
    primaryMargin: Float = 0f,
    secondaryMargin: Float = 0f,
    spacing: Float = 0f,
    builder: CustomPanelAPI.() -> Unit = {}
): CustomPanelAPI {
    // Create the container panel
    return CustomPanel(this.width, this.height, anchor) {
        builder()

        var previousElement: UIComponentAPI? = null
        var maxSecondaryDimension = 0f
        var startingCoord = 0f

        // Core layout loop
        children.forEachIndexed { index, component ->
            maxSecondaryDimension = max(maxSecondaryDimension, strategy.getSecondaryAxisDimension(component))

            if (index == 0) startingCoord = strategy.anchorFirst(component, alignment, primaryMargin, secondaryMargin)
            else previousElement?.let { prev -> strategy.positionNext(component, prev, alignment, spacing) }
            previousElement = component
        }

        // Calculate final size
        val totalPrimarySize = children.lastOrNull()?.let {
            abs(strategy.getEndEdgeCoordinate(it, alignment) - startingCoord) + primaryMargin * 2
        } ?: (primaryMargin * 2) // Handle empty case

        val totalSecondarySize = maxSecondaryDimension + secondaryMargin * 2

        strategy.setPanelSize(this, totalPrimarySize, totalSecondarySize)
    }
}