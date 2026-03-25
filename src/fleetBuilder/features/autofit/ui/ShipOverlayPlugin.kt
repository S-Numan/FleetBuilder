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

        val fillW = 4f
        val fillH = 16f
        val border = 1f
        val spacing = 1f
        val groupSpacing = 4f

        val step = fillW + spacing

        val rightEdge = pos.x + pos.width - 5f
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

    private fun drawGrid(
        count: Int,
        startX: Float,
        startY: Float,
        cols: Int,
        fillW: Float,
        fillH: Float,
        spacing: Float,
        border: Float,
        topColor: FloatArray,
        midColor: FloatArray
    ) {
        val max = count.coerceAtMost(15)

        for (i in 0 until max) {
            val col = i % cols
            val row = i / cols

            // Position based on FILL size (critical for correct spacing)
            val x = startX + col * (fillW + spacing)
            val y = startY - row * fillH

            // Border first (outside)
            drawBorder(x, y, fillW, fillH, border)

            // Fill inside border
            drawGradientRect(
                x + border,
                y,
                fillW,
                fillH,
                topColor,
                midColor
            )
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

    // Draws a 1px border OUTSIDE the fill area
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

    override fun advance(amount: Float) {}
    override fun processInput(events: List<InputEventAPI?>?) {}
    override fun buttonPressed(buttonId: Any?) {}
}