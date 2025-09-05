package fleetBuilder.ui.popUpUI

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import starficz.onClick
import java.awt.Color


open class BasePopUpUI() : PopUpUI() {
    open var headerTitle: String? = null
    override var x = 15f
    override var y = 45f

    var confirmButton: ButtonAPI? = null
    var cancelButton: ButtonAPI? = null
    var closeButton: ButtonAPI? = null

    var confirmButtonName: String = "Confirm"
    var cancelButtonName: String = "Cancel"

    var doesConfirmForceDismiss: Boolean = true
    var doesCancelForceDismiss: Boolean = true
    var confirmAndCancelAlignment: Alignment = Alignment.RMID

    override fun createUI() {
        createHeader()

        createContentForDialog()
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
                if (doesConfirmForceDismiss)
                    forceDismiss()
                applyConfirmScript()
            }
        }
        if (cancelButton != null) {
            if (cancelButton!!.isChecked) {
                cancelButton!!.isChecked = false
                if (doesCancelForceDismiss)
                    forceDismiss()
                applyCancelScript()
            }
        }
    }

    open fun applyConfirmScript() {
    }

    open fun applyCancelScript() {
    }

    fun generateConfirmButton(tooltip: TooltipMakerAPI): ButtonAPI {
        val button = tooltip.addButton(confirmButtonName, "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 160f, 25f, 0f)
        button.setShortcut(Keyboard.KEY_G, true)
        confirmButton = button
        return button
    }

    fun generateCancelButton(tooltip: TooltipMakerAPI): ButtonAPI {
        val button = tooltip.addButton(cancelButtonName, "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, buttonConfirmWidth, 25f, 0f)
        button.setShortcut(Keyboard.KEY_ESCAPE, true)
        cancelButton = button
        return button
    }

    fun createConfirmAndCancelSection(
        addConfirmButton: Boolean = true,
        addCancelButton: Boolean = true,
    ) {
        val totalWidth = this@BasePopUpUI.panel.position.width
        val tooltip = panel.createUIElement(totalWidth, 25f, false)
        tooltip.setButtonFontOrbitron20()

        val spacing = 10f
        val totalButtonWidth = (if (addConfirmButton) buttonConfirmWidth else 0f) +
                (if (addCancelButton) buttonConfirmWidth else 0f) +
                (if (addConfirmButton && addCancelButton) spacing else 0f)

        val startX = when (confirmAndCancelAlignment) {
            Alignment.LMID -> 0f
            Alignment.MID -> (totalWidth - totalButtonWidth) / 2f
            Alignment.RMID -> totalWidth - totalButtonWidth
            else -> 0f
        }

        var xPos = startX
        if (addConfirmButton) {
            generateConfirmButton(tooltip)
            confirmButton!!.position.inTL(xPos, 0f)
            xPos += buttonConfirmWidth + spacing
        }
        if (addCancelButton) {
            generateCancelButton(tooltip)
            cancelButton!!.position.inTL(xPos, 0f)
        }

        val bottom = originalSizeY
        val alignX = when (confirmAndCancelAlignment) {
            Alignment.LMID -> x
            Alignment.MID -> 0f
            Alignment.RMID -> -x
            else -> 0f
        }
        panel.addUIElement(tooltip).inTL(alignX, bottom - 40)
    }

    fun addCloseButton() {
        val buttonSize = 32f
        val ui = panel.createUIElement(buttonSize, buttonSize, false)

        ui.setButtonFontOrbitron20Bold()
        closeButton = ui.addButton(
            "X",
            "close_button",
            Color.WHITE,
            Color.RED.darker().darker(),
            Alignment.MID,
            CutStyle.TL_BR,
            buttonSize,
            buttonSize,
            0f
        )

        // Position in top-right
        panel.addUIElement(ui).inTR(x / 2, x / 2 - 4f)

        closeButton?.onClick { forceDismiss() }
    }

    open fun createContentForDialog() {

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

    val auxYPad = 10f
    var headerTooltip: TooltipMakerAPI? = null
    fun createHeader() {

        if (headerTitle != null) {
            headerTooltip = panel.createUIElement(panel.position.width - (x * 2), 20f, false)
            headerTooltip!!.setParaFont(Fonts.ORBITRON_20AABOLD)
            val label = headerTooltip!!.addPara(headerTitle, Misc.getTooltipTitleAndLightHighlightColor(), 5f)
            panel.addUIElement(headerTooltip).inTL(x, auxYPad)
            val textWidth = label.computeTextWidth(label.text)
            label.position.setLocation(0f, 0f).inTL(((panel.position.width - (x * 2)) - textWidth) / 2f, 3f)
        } else {
            y = auxYPad
        }
    }
}