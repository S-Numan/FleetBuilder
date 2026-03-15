package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.ui.UIUtils.drawRectangleFilledForTooltip
import fleetBuilder.util.FBTxt
import org.lwjgl.input.Keyboard
import starficz.onClick
import java.awt.Color

open class DialogPanel(
    open var headerTitle: String? = null
) : ModalPanel() {
    var confirmButton: ButtonAPI? = null
    var cancelButton: ButtonAPI? = null
    var closeButton: ButtonAPI? = null

    var confirmCancelButtonWidth: Float = 160f

    var doesConfirmDismiss: Boolean = true
    var doesCancelDismiss: Boolean = true

    var confirmButtonShortcut = Keyboard.KEY_G
    var cancelButtonShortcut = Keyboard.KEY_ESCAPE

    override fun createUI() {
        createHeader()

        super.createUI()
    }

    override fun renderBelow(alphaMult: Float) {
        super.renderBelow(alphaMult)

        if (!closing)
            headerTooltip?.let { drawRectangleFilledForTooltip(it, 1f, Global.getSector().playerFaction.darkUIColor.darker()) }
    }

    override fun advance(amount: Float) {
        super.advance(amount)
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

    @JvmOverloads
    fun setupConfirmCancelSection(
        addConfirmButton: Boolean = true,
        addCancelButton: Boolean = true,
        alignment: Alignment = Alignment.RMID,
        confirmText: String = FBTxt.txt("confirm"),
        cancelText: String = FBTxt.txt("cancel"),
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
            confirmButton!!.onClick {
                if (doesConfirmDismiss)
                    dismiss()
                applyConfirmScript()
            }
        }
        if (addCancelButton) {
            val button = tooltip.addButton(cancelText, "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, confirmCancelButtonWidth, 25f, 0f)
            button.setShortcut(cancelButtonShortcut, true)
            button.position.inTL(xPos, 0f)

            cancelButton = button
            cancelButton!!.onClick {
                if (doesCancelDismiss)
                    dismiss()
                applyCancelScript()
            }
        }

        val bottom = goalHeight
        val alignX = when (alignment) {
            Alignment.LMID -> xTooltipPad
            Alignment.MID -> 0f
            Alignment.RMID -> -xTooltipPad
            else -> 0f
        }
        panel.addUIElement(tooltip).inTL(alignX, bottom - 40)
    }

    fun addCloseButton() {
        val buttonSize = 33f
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
        panel.addUIElement(ui).inTR(7f, 2f)

        closeButton!!.onClick { dismiss() }
    }

    override fun getYTooltipPadding(): Float {
        return yTooltipPad + if (headerTitle != null) 30f else 0f
    }

    var headerTooltip: TooltipMakerAPI? = null
    fun createHeader() {
        if (headerTitle != null) {
            headerTooltip = panel.createUIElement(panel.position.width - (xTooltipPad * 3f), 20f, false)
            headerTooltip!!.setParaFont(Fonts.ORBITRON_20AABOLD)
            val label = headerTooltip!!.addPara(headerTitle, Misc.getTooltipTitleAndLightHighlightColor(), 5f)
            panel.addUIElement(headerTooltip).inTL(xTooltipPad * 1.5f, yTooltipPad)
            val textWidth = label.computeTextWidth(label.text)
            label.position.setLocation(0f, 0f).inTL(((panel.position.width - (xTooltipPad * 2)) - textWidth) / 2f, 3f)
        }
    }
}