package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import org.lwjgl.opengl.GL11.*
import java.awt.Color

class UILinesRendererGL(
    private var widthPadding: Float = 10f
) : CustomUIPanelPlugin {
    private val panels = mutableListOf<CustomPanelAPI>()

    var boxColor: Color = Global.getSettings().darkPlayerColor
    var progression: Color = Global.getSettings().brightPlayerColor

    private var renderProgress = false
    private var progress = 0f

    fun setPanels(list: List<CustomPanelAPI>) {
        panels.clear()
        panels.addAll(list)
    }

    fun setPanel(panel: CustomPanelAPI) {
        panels.add(panel)
    }

    fun enableProgressMode(currProgress: Float) {
        renderProgress = true
        progress = currProgress
    }

    override fun render(alphaMult: Float) {
        glPushMatrix()
        glDisable(GL_TEXTURE_2D)

        if (renderProgress) {
            glColor4f(
                progression.red / 255f,
                progression.green / 255f,
                progression.blue / 255f,
                alphaMult
            )

            for (panel in panels) {
                val pos = panel.position

                val width = (pos.width + widthPadding) * progress
                val height = pos.height

                drawRect(pos.x, pos.y, width, height)
            }
        }

        glColor4f(
            boxColor.red / 255f,
            boxColor.green / 255f,
            boxColor.blue / 255f,
            alphaMult
        )

        for (panel in panels) {
            val pos = panel.position

            val w = pos.width + widthPadding
            val h = pos.height

            drawRect(pos.x, pos.y, w, 1f)
            drawRect(pos.x, pos.y + h, w, 1f)

            drawRect(pos.x, pos.y, 1f, h)
            drawRect(pos.x + w, pos.y, 1f, h)
        }

        glEnable(GL_TEXTURE_2D)
        glPopMatrix()
    }

    private fun drawRect(x: Float, y: Float, w: Float, h: Float) {
        glBegin(GL_QUADS)
        glVertex2f(x, y)
        glVertex2f(x + w, y)
        glVertex2f(x + w, y + h)
        glVertex2f(x, y + h)
        glEnd()
    }

    override fun renderBelow(alphaMult: Float) {}
    override fun advance(amount: Float) {}
    override fun processInput(events: MutableList<InputEventAPI>?) {}
    override fun buttonPressed(buttonId: Any?) {}
    override fun positionChanged(position: PositionAPI?) {}
}