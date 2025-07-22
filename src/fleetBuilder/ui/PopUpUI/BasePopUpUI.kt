package fleetBuilder.ui.PopUpUI

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.lwjgl.opengl.GL11
import java.awt.Color


open class BasePopUpUI() : PopUpUI() {
    open var headerTitle: String? = null
    override var x = 15f
    override var y = 45f

    override fun createUI() {
        createHeader(panelToInfluence!!)

        createContentForDialog(panelToInfluence!!)
    }

    override fun renderBelow(alphaMult: Float) {
        super.renderBelow(alphaMult)
        headerTooltip?.let { drawRectangleFilledForTooltip(it, 1f, Global.getSector().playerFaction.darkUIColor.darker()) }
    }

    open fun createContentForDialog(panelAPI: CustomPanelAPI) {

    }

    fun drawRectangleFilledForTooltip(tooltipMakerAPI: TooltipMakerAPI, alphaMult: Float, uiColor: Color?) {
        if (uiColor == null) return

        val x = tooltipMakerAPI.getPosition().getX()
        val y = tooltipMakerAPI.getPosition().getY()
        val w = tooltipMakerAPI.getPosition().getWidth()
        val h = tooltipMakerAPI.getPosition().getHeight()

        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glColor4f(
            uiColor.getRed() / 255f, uiColor.getGreen() / 255f, uiColor.getBlue() / 255f,
            uiColor.getAlpha() / 255f * alphaMult * 23f
        )
        GL11.glRectf(x, y, x + w, y + h)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        GL11.glPopMatrix()
    }

    var headerTooltip: TooltipMakerAPI? = null
    fun createHeader(panelAPI: CustomPanelAPI) {
        val auxYPad = 10f

        if (headerTitle != null) {
            headerTooltip = panelAPI.createUIElement(panelAPI.position.width - 30, 20f, false)
            headerTooltip!!.setParaFont(Fonts.ORBITRON_20AABOLD)
            val label = headerTooltip!!.addPara(headerTitle, Misc.getTooltipTitleAndLightHighlightColor(), 5f)
            panelAPI.addUIElement(headerTooltip).inTL(x, auxYPad)
            val width = label.computeTextWidth(label.text)
            label.position.setLocation(0f, 0f).inTL((panelAPI.position.width / 2) - (width / 2), 3f)
        } else {
            y = auxYPad
        }
    }
}