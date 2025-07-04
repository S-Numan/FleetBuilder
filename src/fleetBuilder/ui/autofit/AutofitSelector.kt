package fleetBuilder.ui.autofit

import MagicLib.*
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
import org.lwjgl.opengl.GL11
import org.magiclib.kotlin.*
import java.awt.Color
import kotlin.math.max

/**
 * Original author
 * @author Starficz
 */
internal object AutofitSelector {
    internal class MagicPaintjobSelectorPlugin(var paintjobSpec: AutofitSpec?) : BaseCustomUIPanelPlugin() {
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
        private var onClickOutsideFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
        private var onClickReleaseFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
        private var onHoverFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
        private var onHoverEnterFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
        private var onHoverExitFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()

        val isUnlocked = true//paintjobSpec == null || paintjobSpec in MagicPaintjobManager.unlockedPaintjobs
        var hasMissing = false
        var isBetter = false
        var isWorse = false
        var isEqual = false

        var isHovering = false
            private set

        var isSelected = false

        var hasClicked = false
            private set

        init {
            onClickFunctions.add {
                if (isUnlocked) clickFader.fadeIn()
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
            val panelAlpha = panelColor.alphaf * alphaMult
            GL11.glColor4f(panelColor.redf, panelColor.greenf, panelColor.bluef, panelAlpha)
            GL11.glRectf(selectorPanel.left, selectorPanel.bottom, selectorPanel.right, selectorPanel.top)

            val darkerBorderColor =
                if (isEqual)
                    Misc.getGrayColor()
                else if (isBetter)
                    Misc.getPositiveHighlightColor().darker().darker()
                else if (isWorse)
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
            if (hasMissing) {
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

                val inElement = event.x.toFloat() in selectorPanel.left..selectorPanel.right &&
                        event.y.toFloat() in selectorPanel.bottom..selectorPanel.top
                if (inElement) {
                    for (onHover in onHoverFunctions) onHover(event)
                    if (!isHovering) onHoverEnterFunctions.forEach { it(event) }
                    isHovering = true
                    if (event.isMouseDownEvent) {
                        hasClicked = true
                        onClickFunctions.forEach { it(event) }
                    }
                    if (event.isMouseUpEvent && hasClicked) {
                        hasClicked = false
                        onClickReleaseFunctions.forEach { it(event) }
                    }
                } else {
                    if (isHovering) onHoverExitFunctions.forEach { it(event) }
                    isHovering = false
                    if (event.isMouseDownEvent) {
                        onClickOutsideFunctions.forEach { it(event) }
                    }
                    if (event.isMouseUpEvent) {
                        hasClicked = false
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

        fun onClickOutside(function: (InputEventAPI) -> Unit) {
            onClickOutsideFunctions.add(function)
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

        override fun advance(amount: Float) {
            highlightFader.advance(amount)
            hoverFader.advance(amount)
            lockedHoverFader.advance(amount)
            clickFader.advance(amount)

            if (isSelected) highlightFader.fadeIn()
            else highlightFader.fadeOut()
        }
    }

    val descriptionHeight = 44f

    internal fun createAutofitSelector(
        hullVariantSpec: HullVariantSpec,
        paintjobSpec: AutofitSpec?,
        width: Float
    ): CustomPanelAPI {

        val plugin = MagicPaintjobSelectorPlugin(paintjobSpec)
        val selectorPanel = Global.getSettings().createCustom(width, width + descriptionHeight, plugin)
        plugin.selectorPanel = selectorPanel

        return createAutofitSelectorChildren(hullVariantSpec, paintjobSpec, width, selectorPanel)
    }

    fun createAutofitSelectorChildren(
        hullVariantSpec: HullVariantSpec,
        paintjobSpec: AutofitSpec?,
        width: Float,
        selectorPanel: CustomPanelAPI
    ): CustomPanelAPI {
        val descriptionYOffset = 2f
        val topPad = 5f

        val shipPreview = createShipPreview(hullVariantSpec, paintjobSpec, width, width)
        selectorPanel.addComponent(shipPreview).inTL(0f, topPad)

        var flipSide = false
        var pushDown = 0f

        var fighterBayCount = 0//hullVariantSpec.launchBaysSlotIds.size)//This does not seem to work?
        if ((hullVariantSpec as ShipVariantAPI).hullSpec.fighterBays > fighterBayCount)
            fighterBayCount = (hullVariantSpec as ShipVariantAPI).hullSpec.fighterBays
        if (hullVariantSpec.wings.size > fighterBayCount)
            fighterBayCount = hullVariantSpec.wings.size

        for (i in 0 until fighterBayCount) {
            val wingSpec = hullVariantSpec.getWing(i)

            val fighterPreview: UIPanelAPI

            if (wingSpec == null) {
                //TODO, display 'fighter_blank' when no fighter is in this slot
                continue

            } else {
                fighterPreview = createShipPreview((wingSpec.variant as HullVariantSpec), null, 64f, 64f)
            }

            val posAPI = selectorPanel.addComponent(fighterPreview)
            if (topPad + pushDown > width - descriptionHeight && !flipSide) {
                pushDown = 0f
                flipSide = true
            }
            if (!flipSide)
                posAPI.inTL(-16f, topPad + pushDown)
            else
                posAPI.inTR(-16f, topPad + pushDown)
            pushDown += 32f
        }

        val textElement = selectorPanel.createUIElement(width, descriptionHeight - topPad, false)
        selectorPanel.addUIElement(textElement)
        with(textElement) {
            position.inTL(0f, width + topPad - descriptionYOffset)
            setTitleOrbitronLarge()
            addTitle(paintjobSpec?.name ?: "Current Variant")
            addPara(paintjobSpec?.description ?: "Click to save", 3f)
        }

        return selectorPanel
    }

    private fun createShipPreview(
        hullVariantSpec: HullVariantSpec, basePaintjobSpec: AutofitSpec?,
        width: Float, height: Float
    ): UIPanelAPI {

        val clonedVariant = hullVariantSpec.clone()
        /*MagicPaintjobManager.removePaintjobFromShip(clonedVariant)
        clonedVariant.moduleVariants?.values?.forEach { moduleVariant ->
            MagicPaintjobManager.removePaintjobFromShip(moduleVariant as ShipVariantAPI)
        }*/

        val shipPreview = ReflectionUtils.instantiate(CombatAutofitAdder.SHIP_PREVIEW_CLASS!!)!!
        ReflectionUtils.invoke("setVariant", shipPreview, clonedVariant)
        ReflectionUtils.invoke("overrideVariant", shipPreview, clonedVariant)
        ReflectionUtils.invoke("setShowBorder", shipPreview, false)
        ReflectionUtils.invoke("setScaleDownSmallerShipsMagnitude", shipPreview, 1f)
        ReflectionUtils.invoke("adjustOverlay", shipPreview, 0f, 0f)
        (shipPreview as UIPanelAPI).setSize(width, height)

        /*for(slot in (clonedVariant as ShipVariantAPI).fittedWeaponSlots){
            val weapon = clonedVariant.getWeaponSpec(slot)
            val sprite = Global.getSettings().getSprite(weapon.turretSpriteName)
            sprite.height
        }*/

        // make the ship list so the ships exist when we try and get them
        ReflectionUtils.invoke("prepareShip", shipPreview)

        // if the paintjob exists, replace the sprites
        /*
        basePaintjobSpec?.let { paintjob ->
            for(ship in ReflectionUtils.get(MagicPaintjobCombatRefitAdder.SHIPS_FIELD!!, shipPreview) as Array<ShipAPI>){
                MagicPaintjobManager.getPaintjobsForHull(ship.hullSpec).firstOrNull {
                    it.paintjobFamily?.equals(paintjob.paintjobFamily) == true || it.id == paintjob.id
                }?.let { MagicPaintjobManager.applyPaintjob(ship, it) }
            }
        }*/

        return shipPreview
    }
}
