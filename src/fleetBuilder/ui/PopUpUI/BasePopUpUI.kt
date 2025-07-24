package fleetBuilder.ui.PopUpUI

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color


open class BasePopUpUI() : PopUpUI() {
    open var headerTitle: String? = null
    override var x = 15f
    override var y = 45f

    var confirmButton: ButtonAPI? = null
    var cancelButton: ButtonAPI? = null

    override fun createUI() {
        createHeader(panelToInfluence!!)

        createContentForDialog(panelToInfluence!!)
    }

    override fun renderBelow(alphaMult: Float) {
        super.renderBelow(alphaMult)
        headerTooltip?.let { drawRectangleFilledForTooltip(it, 1f, Global.getSector().playerFaction.darkUIColor.darker()) }
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        if (confirmButton != null) {
            if (confirmButton!!.isChecked) {
                confirmButton!!.isChecked = false
                applyConfirmScript()
                forceDismiss()
            }
        }
        if (cancelButton != null) {
            if (cancelButton!!.isChecked) {
                cancelButton!!.isChecked = false
                forceDismiss()
            }
        }
    }

    fun generateConfirmButton(tooltip: TooltipMakerAPI): ButtonAPI {
        val button = tooltip.addButton("Confirm", "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 160f, 25f, 0f)
        button.setShortcut(Keyboard.KEY_G, true)
        confirmButton = button
        return button
    }

    fun generateCancelButton(tooltip: TooltipMakerAPI): ButtonAPI {
        val button = tooltip.addButton("Cancel", "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, buttonConfirmWidth, 25f, 0f)
        button.setShortcut(Keyboard.KEY_ESCAPE, true)
        cancelButton = button
        return button
    }

    fun createConfirmAndCancelSection(
        mainPanel: CustomPanelAPI,
        addConfirmButton: Boolean = true,
        addCancelButton: Boolean = true
    ) {
        val totalWidth: Float = buttonConfirmWidth * 2 + 10
        val tooltip = mainPanel.createUIElement(totalWidth, 25f, false)
        tooltip.setButtonFontOrbitron20()
        if (addConfirmButton) {
            generateConfirmButton(tooltip)
            confirmButton!!.position.inTL(0f, 0f)
        }
        if (addCancelButton) {
            generateCancelButton(tooltip)
            cancelButton!!.position.inTL(buttonConfirmWidth + 5, 0f)
        }

        val bottom = originalSizeY
        mainPanel.addUIElement(tooltip).inTL(mainPanel.position.width - (totalWidth) - 10, bottom - 40)
    }

    open fun createContentForDialog(panelAPI: CustomPanelAPI) {

    }

    fun drawRectangleFilledForTooltip(tooltipMakerAPI: TooltipMakerAPI, alphaMult: Float, uiColor: Color?) {
        if (uiColor == null) return

        val x = tooltipMakerAPI.position.x
        val y = tooltipMakerAPI.position.y
        val w = tooltipMakerAPI.position.width
        val h = tooltipMakerAPI.position.height

        GL11.glPushMatrix()
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glColor4f(
            uiColor.red / 255f, uiColor.green / 255f, uiColor.blue / 255f,
            uiColor.alpha / 255f * alphaMult * 23f
        )
        GL11.glRectf(x, y, x + w, y + h)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        GL11.glPopMatrix()
    }

    var headerTooltip: TooltipMakerAPI? = null
    fun createHeader(panelAPI: CustomPanelAPI) {
        val auxYPad = 10f

        if (headerTitle != null) {
            headerTooltip = panelAPI.createUIElement(panelAPI.position.width - (x * 2), 20f, false)
            headerTooltip!!.setParaFont(Fonts.ORBITRON_20AABOLD)
            val label = headerTooltip!!.addPara(headerTitle, Misc.getTooltipTitleAndLightHighlightColor(), 5f)
            panelAPI.addUIElement(headerTooltip).inTL(x, auxYPad)
            val textWidth = label.computeTextWidth(label.text)
            label.position.setLocation(0f, 0f).inTL(((panelAPI.position.width - (x * 2)) - textWidth) / 2f, 3f)
        } else {
            y = auxYPad
        }
    }
}