package fleetBuilder.features.autofit.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.serialization.MissingElements
import fleetBuilder.ui.UIUtils
import fleetBuilder.util.allDMods
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.getEffectiveHullId
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.max

class ShipPreviewOverlayPlugin(
    val member: FleetMemberAPI,
    width: Float, height: Float,
    panel: CustomPanelAPI = Global.getSettings().createCustom(width, height, null),
    showFighters: Boolean = false,
    setSchematicMode: Boolean = false,
    scaleDownSmallerShips: Boolean = false,
    val showSModAndDModBars: Boolean = false,
    val showOfficersAndFlagship: Boolean = false,
    manualScaleShipsToBetterFit: Boolean = false,
    disableScissor: Boolean = false,
    val openCodexWithHotkey: Boolean = false,
    val missingElements: MissingElements = MissingElements()
) : StarUIPanelPlugin() {
    val hasMissingElements: Boolean by lazy { missingElements.weaponIds.isNotEmpty() || missingElements.hullModIds.isNotEmpty() || missingElements.wingIds.isNotEmpty() }
    val isShipMissing: Boolean = member.variant.hasTag(VariantUtils.getFBVariantErrorTag())

    var boxedUIShipPreview: BoxedUIShipPreview? = null

    init {
        this.panel = panel
        
        panel.setPlugin(this)
        //if (!isShipMissing) {
        val shipPreview = BoxedUIShipPreview.FLEETMEMBER_CONSTRUCTOR!!.newInstance(member) as UIPanelAPI
        boxedUIShipPreview = BoxedUIShipPreview(shipPreview)
        boxedUIShipPreview!!.uiShipPreview.setSize(width, height)
        boxedUIShipPreview!!.setBorderNewStyle(true)
        boxedUIShipPreview!!.setShowBorder(false)
        boxedUIShipPreview!!.adjustOverlay(0f, 0f)

        if (showFighters)
            boxedUIShipPreview!!.showFighters = true

        if (setSchematicMode)
            boxedUIShipPreview!!.setSchematicMode(true)

        var scaleDownFactor = 1f


        boxedUIShipPreview!!.setScaleDownSmallerShipsMagnitude(1f)
        if (scaleDownSmallerShips) {
            val hullSize = member.hullSpec.hullSize
            if (hullSize == ShipAPI.HullSize.FRIGATE) {
                scaleDownFactor = 0.5f
            } else if (hullSize == ShipAPI.HullSize.DESTROYER) {
                scaleDownFactor = 0.75f
            } else if (hullSize == ShipAPI.HullSize.CRUISER) {
                scaleDownFactor = 0.9f
            }
        }

        //Remove this hard coded scaling code when things scale right properly in the base game.

        val effectiveHullId = member.hullSpec.getEffectiveHullId()

        // Define config for special ships
        data class ShipDisplayConfig(
            val scaleFactor: Float = 1f,
            val yOffset: Float = 0f,
            val disableScissor: Boolean = false
        )

        val specialConfigs = if (manualScaleShipsToBetterFit) {
            // Configurations for special hull IDs
            mapOf(
                "apogee" to ShipDisplayConfig(scaleFactor = 0.9f, yOffset = 11f, disableScissor = true),
                "radiant" to ShipDisplayConfig(scaleFactor = 0.95f, yOffset = 10f, disableScissor = true),
                "paragon" to ShipDisplayConfig(scaleFactor = 0.94f, yOffset = 15f, disableScissor = true),
                "pegasus" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 7f, disableScissor = true),
                "executor" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 7f, disableScissor = true),
                "invictus" to ShipDisplayConfig(scaleFactor = 0.98f, yOffset = 0f, disableScissor = true),
                "onslaught" to ShipDisplayConfig(scaleFactor = 1.08f, yOffset = 0f, disableScissor = false),
                "hammerhead" to ShipDisplayConfig(scaleFactor = 1.00f, yOffset = 4f, disableScissor = false) // If autofit panel is too small, this clips into the top.
            )
        } else emptyMap()

        // Get config or default
        val config = specialConfigs[effectiveHullId] ?: ShipDisplayConfig()

        // Apply config
        if (config.disableScissor || disableScissor) {
            boxedUIShipPreview!!.setScissor(false)
        }

        // Scale and set size
        val scaledWidth = width * config.scaleFactor * scaleDownFactor
        val scaledHeight = height * config.scaleFactor * scaleDownFactor
        boxedUIShipPreview!!.uiShipPreview.setSize(scaledWidth, scaledHeight)

        // Base Y offset from config
        val baseYOffset = config.yOffset

        // Center offsets for shipPreview
        val offsetX = (width - scaledWidth) / 2f
        val offsetY = (height - scaledHeight) / 2f + baseYOffset

        // Add shipPreview to container, positioned to center plus offset
        panel.addComponent(shipPreview).inTL(0f, 0f)
        shipPreview.position.inTL(offsetX, offsetY)
        //}

        if (openCodexWithHotkey) {
            onKeyDown { event ->
                if (event.isConsumed) return@onKeyDown
                if (event.eventValue == Keyboard.KEY_F2 && UIUtils.isMouseHoveringOverComponent(panel, mouseX = event.x, mouseY = event.y)) {
                    Global.getSettings().showCodex(member)
                    event.consume()
                }
            }
        }
    }

    fun cleanup() {
        boxedUIShipPreview?.cleanupShips()
    }

    private lateinit var pos: PositionAPI

    override fun positionChanged(position: PositionAPI?) {
        if (position != null) pos = position
    }

    override fun render(alphaMult: Float) {
        if (!::pos.isInitialized) return

        super.render(alphaMult)

        if (showOfficersAndFlagship)
            renderOfficerAndFlag()

        if (showSModAndDModBars)
            showSModAndDModBars()

        val lockAlpha = 0.7f
        if (!isShipMissing) {
            if (hasMissingElements) {
                val lockSize = 0.7f
                val lockedSprite = Global.getSettings().getSprite("FleetBuilder", "mission_indicator")// !
                lockedSprite.alphaMult = lockAlpha
                lockedSprite.color = Color.YELLOW
                val scaleFactor = lockSize * panel.width / max(lockedSprite.width, lockedSprite.height)
                if (scaleFactor < 2f)
                    lockedSprite.setSize(scaleFactor * lockedSprite.width, scaleFactor * lockedSprite.height)
                lockedSprite.renderAtCenter(panel.centerX, panel.top - panel.width / 2)
            }
        } else {
            val xSprite = Global.getSettings().getSprite("ui", "checkmark_x") // X

            xSprite.color = Misc.interpolateColor(Color.GRAY, Color.RED.darker(), 0.7f)
            xSprite.alphaMult = 0.9f

            var scaleFactor = 1.0f * panel.width / max(xSprite.width, xSprite.height)
            if (scaleFactor > 2f)
                scaleFactor = 2f
            xSprite.setSize(scaleFactor * xSprite.width, scaleFactor * xSprite.height)
            xSprite.renderAtCenter(panel.centerX, panel.top - panel.width / 2)
        }
        //TODO: combat readiness bar
        //TODO: Hull bar
    }

    private fun renderOfficerAndFlag() {
        val pad = 4f

        // Scale relative to panel (keeps it consistent across UI sizes)
        val size = (pos.height * 0.22f).coerceAtLeast(16f).coerceAtMost(32f)

        val x = pos.x + pad
        val y = pos.y + pos.height - pad

        val captain = member.captain
        val hasOfficer = captain != null && !captain.isDefault
        val isFlagship = member.isFlagship

        val highlight = Misc.getDarkPlayerColor()

        glPushMatrix()
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        if (hasOfficer) {
            val sprite = Global.getSettings().getSprite(captain.portraitSprite)

            // Portrait
            sprite.setSize(size, size)
            sprite.setAlphaMult(1f)
            sprite.render(x, y - size)

            // 1px border (true pixel)
            drawPixelBorder(x, y, size, size, highlight)

            // Flagship icon under portrait
            if (isFlagship) {
                val insignia = Global.getSettings()
                    .getSprite("graphics/icons/insignia/16x_star_circle.png")

                insignia.setSize(size, size)
                insignia.render(x, y - size * 2f - 2f)
            }
        } else if (isFlagship) {
            val insignia = Global.getSettings()
                .getSprite("graphics/icons/insignia/16x_star_circle.png")

            insignia.setSize(size, size)
            insignia.render(x, y - size)
        }

        glPopMatrix()
    }

    private fun drawPixelBorder(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Color
    ) {
        val b = 1f // EXACTLY 1 pixel

        glDisable(GL_TEXTURE_2D)
        glColor4f(
            color.red / 255f,
            color.green / 255f,
            color.blue / 255f,
            color.alpha / 255f
        )

        glBegin(GL_QUADS)

        // Top
        glVertex2f(x, y)
        glVertex2f(x + w, y)
        glVertex2f(x + w, y + b)
        glVertex2f(x, y + b)

        // Bottom
        glVertex2f(x, y - h)
        glVertex2f(x + w, y - h)
        glVertex2f(x + w, y - h - b)
        glVertex2f(x, y - h - b)

        // Left
        glVertex2f(x, y)
        glVertex2f(x + b, y)
        glVertex2f(x + b, y - h)
        glVertex2f(x, y - h)

        // Right
        glVertex2f(x + w - b, y)
        glVertex2f(x + w, y)
        glVertex2f(x + w, y - h)
        glVertex2f(x + w - b, y - h)

        glEnd()

        glEnable(GL_TEXTURE_2D)
    }

    private fun showSModAndDModBars() {
        val sMods = member.variant.sMods.size
        val dMods = member.variant.allDMods().size

        if (sMods <= 0 && dMods <= 0) return

        val fillW = 4f
        val fillH = 16f
        val border = 1f
        val spacing = 1f
        val groupSpacing = 4f

        val step = fillW + spacing

        val rightEdge = pos.x + pos.width - 8f
        val topY = pos.y + pos.height - 3f

        glPushMatrix()
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // === DMods (rightmost, grow left) ===
        if (dMods > 0) {
            for (i in 0 until dMods) {
                val x = rightEdge - fillW - i * step
                val y = topY

                drawBorder(x, y, fillW, fillH, border)
                drawGradientRect(
                    x + border,
                    y - border,
                    fillW,
                    fillH,
                    floatArrayOf(255f / 255f, 103f / 255f, 0f),
                    floatArrayOf(255f / 255f, 147f / 255f, 0f)
                )
            }
        }

        // === SMods (to the left of DMods) ===
        if (sMods > 0) {
            val dBlockWidth = if (dMods > 0) {
                dMods * step - spacing // last one has no trailing spacing
            } else 0f

            val startOffset = if (dMods > 0) {
                dBlockWidth + groupSpacing
            } else 0f

            for (i in 0 until sMods) {
                val x = rightEdge - fillW - startOffset - i * step
                val y = topY

                drawBorder(x, y, fillW, fillH, border)
                drawGradientRect(
                    x + border,
                    y - border,
                    fillW,
                    fillH,
                    floatArrayOf(168f / 255f, 246f / 255f, 132f / 255f),
                    floatArrayOf(239f / 255f, 255f / 255f, 188f / 255f)
                )
            }
        }

        glEnable(GL_TEXTURE_2D)
        glPopMatrix()
    }

    private fun drawGradientRect(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        top: FloatArray,
        mid: FloatArray
    ) {
        val bottom = top

        glBegin(GL_QUADS)

        // Top
        glColor3f(top[0], top[1], top[2])
        glVertex2f(x, y)
        glVertex2f(x + w, y)

        // Mid
        glColor3f(mid[0], mid[1], mid[2])
        glVertex2f(x + w, y - h / 2f)
        glVertex2f(x, y - h / 2f)

        // Mid → Bottom
        glColor3f(mid[0], mid[1], mid[2])
        glVertex2f(x, y - h / 2f)
        glVertex2f(x + w, y - h / 2f)

        glColor3f(bottom[0], bottom[1], bottom[2])
        glVertex2f(x + w, y - h)
        glVertex2f(x, y - h)

        glEnd()
    }

    // Draws a 1px border outside the fill area
    private fun drawBorder(x: Float, y: Float, w: Float, h: Float, b: Float) {
        glColor3f(0f, 0f, 0f)

        glBegin(GL_QUADS)

        // Top
        glVertex2f(x, y)
        glVertex2f(x + w + 2 * b, y)
        glVertex2f(x + w + 2 * b, y + b)
        glVertex2f(x, y + b)

        // Bottom
        glVertex2f(x, y - h)
        glVertex2f(x + w + 2 * b, y - h)
        glVertex2f(x + w + 2 * b, y - h - b)
        glVertex2f(x, y - h - b)

        // Left
        glVertex2f(x, y)
        glVertex2f(x + b, y)
        glVertex2f(x + b, y - h)
        glVertex2f(x, y - h)

        // Right
        glVertex2f(x + w + b, y)
        glVertex2f(x + w + 2 * b, y)
        glVertex2f(x + w + 2 * b, y - h)
        glVertex2f(x + w + b, y - h)

        glEnd()
    }
}