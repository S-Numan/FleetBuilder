package fleetBuilder.ui.autofit

import MagicLib.ReflectionUtilsExtra
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.loading.specs.HullVariantSpec
import fleetBuilder.integration.combat.CombatAutofitAdder
import fleetBuilder.util.FBMisc
import fleetBuilder.util.getEffectiveHullId
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.*
import starficz.*
import starficz.ReflectionUtils.invoke
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
            if (!FBMisc.isMouseHoveringOverComponent(selectorPanel)) {
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
        val clonedVariant = variant.clone() as HullVariantSpec

        val shipPreview = ReflectionUtilsExtra.instantiate(CombatAutofitAdder.SHIP_PREVIEW_CLASS!!) as UIPanelAPI
        shipPreview.invoke("setVariant", clonedVariant)
        shipPreview.invoke("overrideVariant", clonedVariant)
        shipPreview.invoke("setShowBorder", false)

        if (!scaleDownSmallerShips)
            shipPreview.invoke("setScaleDownSmallerShipsMagnitude", 1f)

        shipPreview.invoke("adjustOverlay", 0f, 0f)
        shipPreview.setSize(width, height)

        if (showFighters)
            shipPreview.invoke("setShowFighters", true)

        if (setSchematicMode)
            shipPreview.invoke("setSchematicMode", true)

        //Remove this hard coded scaling code when things scale right properly in the base game.

        val effectiveHullId = variant.hullSpec.getEffectiveHullId()

        // Define config for special ships
        data class ShipDisplayConfig(
            val scaleFactor: Float = 1f,
            val yOffset: Float = 0f,
            val disableScissor: Boolean = false
        )

        //val sprite = Global.getSettings().getSprite(clonedVariant.hullSpec.spriteName)
        //minOf(width / sprite.width, height / sprite.height, 1f)//See https://fractalsoftworks.com/forum/index.php?topic=33818.0 for why this cannot work as intended

        // Configurations for special hull IDs
        val specialConfigs = mapOf(
            "apogee" to ShipDisplayConfig(scaleFactor = 0.9f, yOffset = 12f, disableScissor = true),
            "radiant" to ShipDisplayConfig(scaleFactor = 0.95f, yOffset = 10f, disableScissor = true),
            "paragon" to ShipDisplayConfig(scaleFactor = 0.94f, yOffset = 15f, disableScissor = true),
            "pegasus" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 7f, disableScissor = true),
            "executor" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 7f, disableScissor = true),
            "invictus" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 0f, disableScissor = true)
        )

        // Get config or default
        val config = specialConfigs[effectiveHullId] ?: ShipDisplayConfig()

        // Apply config
        if (config.disableScissor) {
            shipPreview.invoke("setScissor", false)
        }

        // Scale and set size
        val scaledWidth = width * config.scaleFactor
        val scaledHeight = height * config.scaleFactor
        shipPreview.setSize(scaledWidth, scaledHeight)

        // Prepare ship
        shipPreview.invoke("prepareShip")

        // Main container panel
        val containerPanel = Global.getSettings().createCustom(width, height, null)

        // Base Y offset from config
        val baseYOffset = config.yOffset

        // Center offsets for shipPreview
        val offsetX = (width - scaledWidth) / 2f
        val offsetY = (height - scaledHeight) / 2f + baseYOffset

        // Add shipPreview to container, positioned to center plus offset
        containerPanel.addComponent(shipPreview).inTL(offsetX, offsetY)

        return containerPanel
    }
}
