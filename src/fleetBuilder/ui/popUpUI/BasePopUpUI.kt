package fleetBuilder.ui.popUpUI

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.util.DialogUtil.initPopUpUI
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import starficz.onClick
import java.awt.Color

open class BasePopUpUI(
    open var headerTitle: String? = null
) : PopUpUI() {

    override var x = 15f
    override var y = 45f

    var confirmButton: ButtonAPI? = null
    var cancelButton: ButtonAPI? = null
    var closeButton: ButtonAPI? = null

    var confirmCancelButtonWidth: Float = 160f

    var doesConfirmForceDismiss: Boolean = true
    var doesCancelForceDismiss: Boolean = true

    var confirmButtonShortcut = Keyboard.KEY_G
    var cancelButtonShortcut = Keyboard.KEY_ESCAPE

    override fun createUI() {
        createHeader()

        createUICallback?.invoke()
    }

    fun createStandardContentArea(): TooltipMakerAPI {
        val buttonWidth = panel.position.width - (x * 2)
        val ui = panel.createUIElement(buttonWidth, panel.position.height, false)
        ui.addSpacer(0f).position.inTL(0f, 0f)
        return ui
    }

    fun addContentArea(ui: TooltipMakerAPI) {
        panel.addUIElement(ui).inTL(x, y)
    }

    private var createUICallback: (() -> Unit)? = null
    fun onCreateUI(
        width: Float = Global.getSettings().screenWidth / 2,
        height: Float = Global.getSettings().screenHeight / 2,
        callback: (TooltipMakerAPI) -> Unit
    ) {
        // store a callback
        createUICallback = {
            // build the UI once here
            val ui = createStandardContentArea()
            // run the user code
            callback(ui)
            // add UI automatically afterward
            addContentArea(ui)
        }

        if (Global.getSector()?.campaignUI != null)
            makeDummyDialog(Global.getSector().campaignUI)

        initPopUpUI(this, width, height)
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

    private var confirmCallback: (() -> Unit)? = null
    private var cancelCallback: (() -> Unit)? = null

    fun onConfirm(callback: () -> Unit) {
        confirmCallback = callback
    }

    fun onCancel(callback: () -> Unit) {
        cancelCallback = callback
    }

    open fun applyConfirmScript() {
        confirmCallback?.invoke()
    }

    open fun applyCancelScript() {
        cancelCallback?.invoke()
    }

    fun setupConfirmCancelSection(
        addConfirmButton: Boolean = true,
        addCancelButton: Boolean = true,
        alignment: Alignment = Alignment.RMID,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
    ) {
        val totalWidth = panel.position.width
        val tooltip = panel.createUIElement(totalWidth, 25f, false)
        tooltip.setButtonFontOrbitron20()

        val spacing = 10f
        val totalButtonWidth = (if (addConfirmButton) confirmCancelButtonWidth else 0f) +
                (if (addCancelButton) confirmCancelButtonWidth else 0f) +
                (if (addConfirmButton && addCancelButton) spacing else 0f)

        val startX = when (alignment) {
            Alignment.LMID -> 0f
            Alignment.MID -> (totalWidth - totalButtonWidth) / 2f
            Alignment.RMID -> totalWidth - totalButtonWidth
            else -> 0f
        }

        var xPos = startX
        if (addConfirmButton) {
            val button = tooltip.addButton(confirmText, "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 160f, 25f, 0f)
            button.setShortcut(confirmButtonShortcut, true)
            button.position.inTL(xPos, 0f)
            xPos += confirmCancelButtonWidth + spacing

            confirmButton = button
        }
        if (addCancelButton) {
            val button = tooltip.addButton(cancelText, "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, confirmCancelButtonWidth, 25f, 0f)
            button.setShortcut(cancelButtonShortcut, true)
            button.position.inTL(xPos, 0f)

            cancelButton = button
        }

        val bottom = originalSizeY
        val alignX = when (alignment) {
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