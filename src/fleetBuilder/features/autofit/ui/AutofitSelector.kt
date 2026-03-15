package fleetBuilder.features.autofit.ui

import MagicLib.ReflectionUtilsExtra
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.combat.BoundsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.ui.UIUtils
import fleetBuilder.util.getEffectiveHullId
import fleetBuilder.util.safeInvoke
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.*
import starficz.*
import java.awt.Color
import kotlin.math.max

/**
 * Original author
 * @author Starficz
 */
internal object AutofitSelector {
    internal class AutofitSelectorPlugin(var autofitSpec: AutofitSpec?, var addXIfAutofitSpecNull: Boolean = false) :
        BaseCustomUIPanelPlugin() {
        lateinit var selectorPanel: CustomPanelAPI

        val defaultBGColor: Color = Color.BLACK
        val clickedBGColor: Color = Misc.getDarkPlayerColor()
        val hoverBGColor: Color = Misc.getDarkPlayerColor().darker()
        val highlightBGColor: Color = Misc.getDarkPlayerColor().darker().darker()
        val lockedColor: Color = Color.BLACK.setAlpha(170)
        val lockAlpha: Float = 0.7f
        val lockSize: Float = 0.5f // 50%

        val highlightFader = FaderUtil(0.0F, 0.05F, 0.25F)
        private val hoverFader = FaderUtil(0.0F, 0.05F, 0.25F)
        private val lockedHoverFader = FaderUtil(0.0F, 0.25F, 0.5F)
        private val clickFader = FaderUtil(0.0F, 0.05F, 0.25F)

        private var onClickFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
        private var onPressOutsideFunctions: MutableList<() -> Unit> = ArrayList()
        private var onClickReleaseFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
        private var onClickReleaseOutsideFunctions: MutableList<() -> Unit> = ArrayList()
        private var onClickReleaseNoInitClickFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
        private var onHoverFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
        private var onHoverEnterFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
        private var onHoverExitFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()

        var isBase = false
        val isUnlocked = true
        var noClickFader = false

        var draggingAutofitSpec = false

        enum class ComparisonStatus {
            DEFAULT, BETTER, WORSE, EQUAL
        }

        var comparisonStatus = ComparisonStatus.DEFAULT
        var diffWeaponGroups = false
        var diffFluxStats = false

        var isHovering = false
            private set

        var isSelected = false

        var hasClicked = false
            private set

        init {
            onClickFunctions.add {
                if (isUnlocked && !noClickFader) clickFader.fadeIn()
            }
            onClickReleaseFunctions.add { clickFader.fadeOut() }
            onHoverEnterFunctions.add {
                if (isUnlocked) hoverFader.fadeIn()
                lockedHoverFader.fadeIn()
            }
            onHoverExitFunctions.add {
                clickFader.fadeOut()
                hoverFader.fadeOut()
                lockedHoverFader.fadeOut()
            }
        }

        override fun renderBelow(alphaMult: Float) {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_BLEND)

            // interpolate all the different faders together for the flash color
            var panelColor = Misc.interpolateColor(defaultBGColor, highlightBGColor, highlightFader.brightness)
            panelColor = Misc.interpolateColor(panelColor, hoverBGColor, hoverFader.brightness)
            panelColor = Misc.interpolateColor(panelColor, clickedBGColor, clickFader.brightness)
            if (isBase) panelColor = Misc.interpolateColor(panelColor, Color.BLACK, 0.4f)

            val panelAlpha = panelColor.alphaf * alphaMult
            GL11.glColor4f(panelColor.redf, panelColor.greenf, panelColor.bluef, panelAlpha)
            GL11.glRectf(selectorPanel.left, selectorPanel.bottom, selectorPanel.right, selectorPanel.top)

            val darkerBorderColor =
                if (comparisonStatus == ComparisonStatus.EQUAL)
                    Misc.getGrayColor().darker()
                else if (comparisonStatus == ComparisonStatus.BETTER)
                    Misc.getPositiveHighlightColor().darker().darker()
                else if (comparisonStatus == ComparisonStatus.WORSE)
                    Misc.getNegativeHighlightColor().darker().darker()
                else
                    Misc.getDarkPlayerColor().darker()

            val darkerBorderAlpha = darkerBorderColor.alphaf * alphaMult

            GL11.glColor4f(darkerBorderColor.redf, darkerBorderColor.greenf, darkerBorderColor.bluef, darkerBorderAlpha)
            drawBorder(selectorPanel.left, selectorPanel.bottom, selectorPanel.right, selectorPanel.top)

            GL11.glPopMatrix()
        }

        override fun render(alphaMult: Float) {
            GL11.glPushMatrix()

            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            if (!isUnlocked) {
                val lockedAlpha = Misc.interpolate(lockedColor.alphaf, 0f, lockedHoverFader.brightness) * alphaMult
                GL11.glColor4f(lockedColor.redf, lockedColor.greenf, lockedColor.bluef, lockedAlpha)
                GL11.glRectf(selectorPanel.left, selectorPanel.bottom, selectorPanel.right, selectorPanel.top)

                val lockedSprite = Global.getSettings().getSprite("icons", "lock")
                lockedSprite.alphaMult = Misc.interpolate(lockAlpha, 0f, lockedHoverFader.brightness)
                val scaleFactor = lockSize * selectorPanel.width / max(lockedSprite.width, lockedSprite.height)
                if (scaleFactor < 1)
                    lockedSprite.setSize(scaleFactor * lockedSprite.width, scaleFactor * lockedSprite.height)
                lockedSprite.renderAtCenter(selectorPanel.centerX, selectorPanel.top - selectorPanel.width / 2)
            }
            //
            if (autofitSpec != null) {
                if (autofitSpec!!.missing.hasMissing()) {
                    val lockedAlpha = Misc.interpolate(lockedColor.alphaf, 0f, lockedHoverFader.brightness) * alphaMult
                    GL11.glColor4f(lockedColor.redf, lockedColor.greenf, lockedColor.bluef, lockedAlpha)
                    GL11.glRectf(selectorPanel.left, selectorPanel.bottom, selectorPanel.right, selectorPanel.top)

                    val lockedSprite = Global.getSettings().getSprite("FleetBuilder", "mission_indicator")
                    lockedSprite.alphaMult = Misc.interpolate(lockAlpha, 0f, lockedHoverFader.brightness)
                    lockedSprite.color = Color.RED
                    val scaleFactor = lockSize * selectorPanel.width / max(lockedSprite.width, lockedSprite.height)
                    if (scaleFactor < 1)
                        lockedSprite.setSize(scaleFactor * lockedSprite.width, scaleFactor * lockedSprite.height)
                    lockedSprite.renderAtCenter(selectorPanel.centerX, selectorPanel.top - selectorPanel.width / 2)
                }
                if (diffWeaponGroups) {
                    val diffWGSprite = Global.getSettings().getSprite("FleetBuilder", "different_weapon_groups")
                    diffWGSprite.render(selectorPanel.x + selectorPanel.width - diffWGSprite.width, selectorPanel.y + diffWGSprite.height)
                }
                if (diffFluxStats) {
                    val diffFSSprite = Global.getSettings().getSprite("FleetBuilder", "different_flux_stats")
                    diffFSSprite.render(selectorPanel.x + selectorPanel.width - diffFSSprite.width, selectorPanel.y)
                }
            } else if (addXIfAutofitSpecNull) {
                val xSprite = Global.getSettings().getSprite("FleetBuilder", "entity")
                //val xButton = Global.getSettings().getSprite("ui", "checkmark_x")
                //xSprite.alphaMult = Misc.interpolate(0.5f, 0.75f, hoverFader.brightness)
                if (draggingAutofitSpec)
                    xSprite.color = Misc.interpolateColor(Color.GRAY, Color.RED.darker(), lockedHoverFader.brightness)
                else
                    xSprite.color = Misc.interpolateColor(Color.GRAY, Color.YELLOW.darker().darker(), lockedHoverFader.brightness)

                val scaleFactor = 1.0f * selectorPanel.width / max(xSprite.width, xSprite.height)
                if (scaleFactor < 1)
                    xSprite.setSize(scaleFactor * xSprite.width, scaleFactor * xSprite.height)
                xSprite.renderAtCenter(selectorPanel.centerX, selectorPanel.top - selectorPanel.width / 2)
            }
            GL11.glPopMatrix()
        }

        private fun drawBorder(x1: Float, y1: Float, x2: Float, y2: Float) {
            GL11.glRectf(x1, y1, x2 + 1, y1 - 1)
            GL11.glRectf(x2, y1, x2 + 1, y2 + 1)
            GL11.glRectf(x1, y2, x1 - 1, y1 - 1)
            GL11.glRectf(x2, y2, x1 - 1, y2 + 1)
        }

        override fun processInput(events: MutableList<InputEventAPI>?) {
            events!!.filter { it.isMouseEvent }.forEach { event ->

                if (event.isConsumed) return@forEach
                val inElement = event.x.toFloat() in selectorPanel.left..selectorPanel.right &&
                        event.y.toFloat() in selectorPanel.bottom..selectorPanel.top
                if (inElement) {
                    for (onHover in onHoverFunctions) onHover(event)
                    if (!isHovering) onHoverEnterFunctions.forEach { it(event) }
                    isHovering = true
                    if (event.isMouseDownEvent) {
                        hasClicked = true
                        onClickFunctions.forEach { it(event) }
                        event.consume()
                    }
                    if (event.isMouseUpEvent) {
                        if (hasClicked) {
                            hasClicked = false
                            onClickReleaseFunctions.forEach { it(event) }
                        } else {
                            onClickReleaseNoInitClickFunctions.forEach { it(event) }
                        }
                    }
                } else {
                    if (isHovering) onHoverExitFunctions.forEach { it(event) }
                    isHovering = false
                    //if (event.isMouseDownEvent) {
                    //    onPressClickFunctions.forEach { it() }
                    //}
                    if (event.isMouseUpEvent && hasClicked) {
                        hasClicked = false
                        onClickReleaseOutsideFunctions.forEach { it() }
                    }
                }
            }
        }

        fun onClick(function: (InputEventAPI) -> Unit) {
            onClickFunctions.add(function)
        }

        fun onClickRelease(function: (InputEventAPI) -> Unit) {
            onClickReleaseFunctions.add(function)
        }

        fun onClickReleaseOutside(function: () -> Unit) {
            onClickReleaseOutsideFunctions.add(function)
        }

        fun onClickReleaseNoInitClick(function: (InputEventAPI) -> Unit) {
            onClickReleaseNoInitClickFunctions.add(function)
        }

        fun onPressOutside(function: () -> Unit) {
            onPressOutsideFunctions.add(function)
        }

        fun onHover(function: (InputEventAPI) -> Unit) {
            onHoverFunctions.add(function)
        }

        fun onHoverEnter(function: (InputEventAPI) -> Unit) {
            onHoverEnterFunctions.add(function)
        }

        fun onHoverExit(function: (InputEventAPI) -> Unit) {
            onHoverExitFunctions.add(function)
        }

        var mouseUp = false
        override fun advance(amount: Float) {
            if (!UIUtils.isMouseHoveringOverComponent(selectorPanel)) {
                if (hasClicked && !Mouse.isButtonDown(0)) {
                    if (mouseUp) {
                        onClickReleaseOutsideFunctions.forEach { it() }
                        hasClicked = false
                        mouseUp = false
                    } else {
                        mouseUp = true
                    }
                }
                if (!isHovering && Mouse.isButtonDown(0)) {
                    onPressOutsideFunctions.forEach { it() }
                }
            }

            highlightFader.advance(amount)
            hoverFader.advance(amount)
            lockedHoverFader.advance(amount)
            clickFader.advance(amount)

            if (isSelected) highlightFader.fadeIn()
            else highlightFader.fadeOut()
        }
    }

    val titleHeight = 26f
    val descriptionHeight = 18f

    internal fun createAutofitSelector(
        autofitSpec: AutofitSpec?,
        width: Float,
        addTitle: Boolean = true,
        centerTitle: Boolean = false,
        addDescription: Boolean = true,
        addXIfAutofitSpecNull: Boolean = false
    ): CustomPanelAPI {

        val plugin = AutofitSelectorPlugin(autofitSpec, addXIfAutofitSpecNull)
        val selectorPanel = Global.getSettings().createCustom(width, width + (if (addTitle) titleHeight else 0f) + (if (addDescription) descriptionHeight else 0f), plugin)
        plugin.selectorPanel = selectorPanel

        if (autofitSpec != null)
            createAutofitSelectorChildren(autofitSpec, width, selectorPanel, addTitle = addTitle, addDescription = addDescription, centerTitle = centerTitle)
        else
            plugin.noClickFader = true

        return selectorPanel
    }

    fun createAutofitSelectorChildren(
        autofitSpec: AutofitSpec,
        width: Float,
        selectorPanel: CustomPanelAPI,
        addTitle: Boolean = true,
        centerTitle: Boolean = false,
        addDescription: Boolean = true
    ) {
        val descriptionYOffset = 2f
        val topPad = 5f

        val shipPreview = createShipPreview(autofitSpec.variant, width, width, showFighters = true)
        selectorPanel.addComponent(shipPreview).inTL(0f, topPad)

        if (!addTitle && !addDescription) return

        val textElement = selectorPanel.createUIElement(width, (if (addTitle) titleHeight else 0f) + (if (addDescription) descriptionHeight else 0f) - topPad, false)
        selectorPanel.addUIElement(textElement)
        with(textElement) {
            position.inTL(0f, width + topPad - descriptionYOffset)
            setTitleOrbitronLarge()
            val label = addTitle(autofitSpec.variant.displayName)
            label.autoSizeToText(autofitSpec.variant.displayName)
            if (centerTitle)
                label.position.inTL((width - label.computeTextWidth(label.text)) / 2f, -topPad)

            if (autofitSpec.description.isNotEmpty() && addDescription) {
                val description = addPara(autofitSpec.description, 3f)
                description.autoSizeToText(autofitSpec.description)
            }
        }
    }

    fun createShipPreview(
        variant: ShipVariantAPI,
        width: Float, height: Float,
        scaleDownSmallerShips: Boolean = false,
        showFighters: Boolean = false,
        setSchematicMode: Boolean = false,
    ): UIPanelAPI {
        // Main container panel
        val containerPanel = Global.getSettings().createCustom(width, height, null)

        val clonedVariant = variant.clone() as HullVariantSpec

        val shipPreview = ReflectionUtilsExtra.instantiate(BoxedUIShipPreview.SHIP_PREVIEW_CLASS!!) as UIPanelAPI

        shipPreview.safeInvoke("setVariant", clonedVariant)
        shipPreview.safeInvoke("overrideVariant", clonedVariant)
        shipPreview.safeInvoke("setShowBorder", false)

        if (!scaleDownSmallerShips)
            shipPreview.safeInvoke("setScaleDownSmallerShipsMagnitude", 1f)

        shipPreview.safeInvoke("adjustOverlay", 0f, 0f)
        shipPreview.setSize(width, height)

        if (showFighters)
            shipPreview.safeInvoke("setShowFighters", true)

        if (setSchematicMode)
            shipPreview.safeInvoke("setSchematicMode", true)


        //Remove this hard coded scaling code when things scale right properly in the base game.

        val effectiveHullId = variant.hullSpec.getEffectiveHullId()

        // Define config for special ships
        data class ShipDisplayConfig(
            val scaleFactor: Float = 1f,
            val yOffset: Float = 0f,
            val disableScissor: Boolean = false
        )

        //val padding = computeSpritePaddingPixels(clonedVariant)
        //val sprite = Global.getSettings().getSprite(clonedVariant.hullSpec.spriteName)
        //minOf(width / sprite.width, height / sprite.height, 1f)//See https://fractalsoftworks.com/forum/index.php?topic=33818.0 for why this cannot work as intended

        // Configurations for special hull IDs
        val specialConfigs = mapOf(
            "apogee" to ShipDisplayConfig(scaleFactor = 0.9f, yOffset = 12f, disableScissor = true),
            "radiant" to ShipDisplayConfig(scaleFactor = 0.95f, yOffset = 10f, disableScissor = true),
            "paragon" to ShipDisplayConfig(scaleFactor = 0.94f, yOffset = 15f, disableScissor = true),
            "pegasus" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 7f, disableScissor = true),
            "executor" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 7f, disableScissor = true),
            "invictus" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 0f, disableScissor = true),
            "onslaught" to ShipDisplayConfig(scaleFactor = 1.08f, yOffset = 0f, disableScissor = false)
        )

        // Get config or default
        val config = specialConfigs[effectiveHullId] ?: ShipDisplayConfig()

        // Apply config
        if (config.disableScissor) {
            shipPreview.safeInvoke("setScissor", false)
        }

        // Scale and set size
        val scaledWidth = width * config.scaleFactor
        val scaledHeight = height * config.scaleFactor
        shipPreview.setSize(scaledWidth, scaledHeight)

        // Prepare ship
        shipPreview.safeInvoke("prepareShip")

        // Base Y offset from config
        val baseYOffset = config.yOffset

        // Center offsets for shipPreview
        val offsetX = (width - scaledWidth) / 2f
        val offsetY = (height - scaledHeight) / 2f + baseYOffset

        // Add shipPreview to container, positioned to center plus offset
        containerPanel.addComponent(shipPreview).inTL(offsetX, offsetY)

        return containerPanel
    }

    private fun getExactBounds(variant: ShipVariantAPI): List<BoundsAPI.SegmentAPI>? {
        val tempShipPreview = ReflectionUtilsExtra.instantiate(BoxedUIShipPreview.SHIP_PREVIEW_CLASS!!) as UIPanelAPI
        tempShipPreview.safeInvoke("setVariant", variant)
        tempShipPreview.safeInvoke("overrideVariant", variant)
        tempShipPreview.safeInvoke("prepareShip")

        @Suppress("UNCHECKED_CAST")
        val ships = tempShipPreview.safeInvoke("getShips") as? Array<ShipAPI>
        val ship = ships?.getOrNull(0) ?: return null
        return ship.exactBounds?.segments

        //ships.getOrNull(0)?.visualBounds
        //ships.getOrNull(0)?.getNearestPointOnBounds()
        //ships.getOrNull(0)?.getCollisionPoint()
        //ships.getOrNull(0)?.isPointInBounds()
        //ships.getOrNull(0)?.exactBounds
        //ships.getOrNull(0)?.checkCollisionVsRay()

    }

    data class BoundsDimensions(
        val width: Float,
        val height: Float,
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    )

    fun computeBoundsDimensions(variant: ShipVariantAPI): BoundsDimensions? {
        val bounds = getExactBounds(variant) ?: return null
        if (bounds.isEmpty()) return null

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (segment in bounds) {

            val p1 = segment.p1
            val p2 = segment.p2

            // Rotate 90 degrees left: (x, y) -> (-y, x)
            val p1x = -p1.y
            val p1y = p1.x

            val p2x = -p2.y
            val p2y = p2.x

            minX = minOf(minX, p1x, p2x)
            minY = minOf(minY, p1y, p2y)
            maxX = maxOf(maxX, p1x, p2x)
            maxY = maxOf(maxY, p1y, p2y)
        }

        return BoundsDimensions(
            width = maxX - minX,
            height = maxY - minY,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
    }

    data class SpritePaddingPixels(
        val emptyWidth: Int,
        val emptyHeight: Int
    )

    fun computeSpritePaddingPixels(variant: ShipVariantAPI): SpritePaddingPixels? {
        val sprite = Global.getSettings().getSprite(variant.hullSpec.spriteName)
        val bounds = computeBoundsDimensions(variant) ?: return null

        val spriteWidth = sprite.width
        val spriteHeight = sprite.height

        val halfW = spriteWidth / 2f
        val halfH = spriteHeight / 2f

        // Convert bounds from center-space to sprite-space
        val minXSprite = bounds.minX + halfW
        val maxXSprite = bounds.maxX + halfW
        val minYSprite = bounds.minY + halfH
        val maxYSprite = bounds.maxY + halfH

        // Empty space on each side
        val leftEmpty = minXSprite.toInt()
        val rightEmpty = (spriteWidth - maxXSprite).toInt()
        val bottomEmpty = minYSprite.toInt()
        val topEmpty = (spriteHeight - maxYSprite).toInt()

        // Total empty space dimensions
        val emptyWidth = leftEmpty + rightEmpty
        val emptyHeight = topEmpty + bottomEmpty

        return SpritePaddingPixels(
            emptyWidth = emptyWidth,
            emptyHeight = emptyHeight
        )
    }

    fun debugTestingFunction(hull: ShipHullSpecAPI) {
        val sprite = Global.getSettings().getSprite(hull.spriteName)

        // Actual dimensions of the file (png, webp, etc). Includes empty space on sides of the image
        sprite.width
        sprite.height

        // How much the sprite takes up of the texture in OpenGL on a scale of 0 to 1. Return value is based on the sprite width/height divided by the next highest power of two. Next highest power may be different between width and height.
        sprite.textureWidth // 0.5625 = 288(sprite.width) / 512(next highest power of two above 288)
        sprite.textureHeight // 0.75 = 384(sprite.height) / 512(next highest power of two above 384)

        //Onslaught
        //Width = 0.5625 = 288 / 512
        //Height = 0.75 = 384 / 512

        //Apogee
        //Width = 0.546875 = 140 / 256
        //Height = 0.546875 = 280 / 512

        //Executor
        //Width = 0.546875 = 280 / 512
        //Height = 0.69921875 = 358 / 512

        //Atlas
        //Width 0.78125 = 200 / 256
        //Height 0.78125 = 400 / 512

        //Astral
        //Width 0.625 = 320 / 512
        //Height 0.859375 = 440 / 512

        //Nova
        //Width 0.796875 = 204 / 256
        //Height 0.625 = 320 / 512


        // UV coordinates on the texture made that was scaled up to the next power of two. texX and texY are typically 0.0f, and OpenGL follows bottom left origin.
        sprite.texX
        sprite.texY
        sprite.texWidth
        sprite.texHeight


        /*
        val padding = computeSpritePaddingPixels(hull.createHullVariant()) ?: return
        var weaponAtFront = false
        if (padding.emptyHeight < 20 // Less than x padding?
        //&& sprite.height > shipDisplay.height // Sprite bigger than shipDisplay?
        ) {
            val spriteRealHeightCenter = (sprite.height / 2f)
            val spriteHeightCenterDiff = spriteRealHeightCenter - sprite.centerY
            hull.allWeaponSlotsCopy.forEach {
                if (it.slotSize == WeaponSize.LARGE // Slot is large?
                    && it.location.x > sprite.height / 2f - 20f // Slot is within 20 pixels from the front of the ship?
                ) {
                    weaponAtFront = true
                    return@forEach
                }
            }
        }
        if (weaponAtFront) {
            //Scale down?
        }
        */

    }
}