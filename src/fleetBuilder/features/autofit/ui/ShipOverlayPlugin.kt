package fleetBuilder.features.autofit.ui

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.PositionAPI
import fleetBuilder.util.allDMods
import org.lwjgl.opengl.GL11.*

class ShipOverlayPlugin(
    private val member: FleetMemberAPI
) : CustomUIPanelPlugin {

    private lateinit var pos: PositionAPI

    override fun positionChanged(position: PositionAPI?) {
        if (position != null) pos = position
    }

    override fun renderBelow(alphaMult: Float) {}

    override fun render(alphaMult: Float) {
        if (!::pos.isInitialized) return

        val sMods = member.variant.sMods.size
        val dMods = member.variant.allDMods().size

        if (sMods <= 0 && dMods <= 0) return

        val rectWidth = 4f
        val rectHeight = 16f
        val spacing = 1f
        val groupSpacing = 4f

        val cols = 5
        val rows = 3

        val groupWidth = cols * rectWidth + (cols - 1) * spacing
        val startXRight = pos.x + pos.width - groupWidth
        val startYTop = pos.y + pos.height - rectHeight

        glPushMatrix()
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        var currentX = startXRight

        // If both exist, shift SMods left
        if (sMods > 0 && dMods > 0) {
            currentX -= (groupWidth + groupSpacing)
        }

        // Draw SMods
        if (sMods > 0) {
            drawGrid(
                count = sMods,
                startX = currentX,
                startY = startYTop,
                cols = cols,
                rectWidth = rectWidth,
                rectHeight = rectHeight,
                spacing = spacing,
                topColor = floatArrayOf(168f / 255f, 246f / 255f, 132f / 255f),
                midColor = floatArrayOf(239f / 255f, 255f / 255f, 188f / 255f)
            )
        }

        // Draw DMods
        if (dMods > 0) {
            val dmodX = if (sMods > 0) {
                currentX + groupWidth + groupSpacing
            } else {
                currentX
            }

            drawGrid(
                count = dMods,
                startX = dmodX,
                startY = startYTop,
                cols = cols,
                rectWidth = rectWidth,
                rectHeight = rectHeight,
                spacing = spacing,
                topColor = floatArrayOf(255f / 255f, 103f / 255f, 0f / 255f),
                midColor = floatArrayOf(255f / 255f, 147f / 255f, 0f / 255f)
            )
        }

        glEnable(GL_TEXTURE_2D)
        glPopMatrix()
    }

    private fun drawGrid(
        count: Int,
        startX: Float,
        startY: Float,
        cols: Int,
        rectWidth: Float,
        rectHeight: Float,
        spacing: Float,
        topColor: FloatArray,
        midColor: FloatArray
    ) {
        for (i in 0 until count.coerceAtMost(15)) {
            val col = i % cols
            val row = i / cols

            val x = startX + col * (rectWidth + spacing)
            val y = startY - row * (rectHeight + spacing)

            drawGradientRect(x, y, rectWidth, rectHeight, topColor, midColor)
            drawBorder(x, y, rectWidth, rectHeight)
        }
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

        // Middle
        glColor3f(mid[0], mid[1], mid[2])
        glVertex2f(x + w, y - h / 2f)
        glVertex2f(x, y - h / 2f)

        // Middle → Bottom
        glColor3f(mid[0], mid[1], mid[2])
        glVertex2f(x, y - h / 2f)
        glVertex2f(x + w, y - h / 2f)

        glColor3f(bottom[0], bottom[1], bottom[2])
        glVertex2f(x + w, y - h)
        glVertex2f(x, y - h)

        glEnd()
    }

    private fun drawBorder(x: Float, y: Float, w: Float, h: Float) {
        glColor3f(0f, 0f, 0f)
        glLineWidth(1f)

        glBegin(GL_LINE_LOOP)
        glVertex2f(x, y)
        glVertex2f(x + w, y)
        glVertex2f(x + w, y - h)
        glVertex2f(x, y - h)
        glEnd()
    }

    override fun advance(amount: Float) {}
    override fun processInput(events: List<InputEventAPI?>?) {}
    override fun buttonPressed(buttonId: Any?) {}
}
