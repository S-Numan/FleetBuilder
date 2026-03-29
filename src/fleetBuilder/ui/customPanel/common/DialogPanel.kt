package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.ui.UIUtils.drawRectangleFilledForTooltip
import fleetBuilder.util.FBTxt
import org.lwjgl.input.Keyboard
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

    override var dialogStyle: Boolean = true
    override var darkenBackground: Boolean = true

    override fun createUI() {
        createHeader()

        super.createUI()
    }

    override fun renderBelow(alphaMult: Float) {
        super.renderBelow(alphaMult)

        if (!closing)
            headerTooltip?.let { drawRectangleFilledForTooltip(it, 1f, Global.getSector().playerFaction.darkUIColor.darker()) }
    }

    private var confirmCallback: (() -> Unit)? = null
    private var cancelCallback: (() -> Unit)? = null

    fun onConfirm(callback: () -> Unit) {
        confirmCallback = callback
    }

    fun onCancel(callback: () -> Unit) {
        cancelCallback = callback
    }

    protected open fun applyConfirmScript() {
        confirmCallback?.invoke()
    }

    protected open fun applyCancelScript() {
        cancelCallback?.invoke()
    }

    @JvmOverloads
    fun addActionButtons(
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
                applyConfirmScript()
                if (doesConfirmDismiss)
                    dismiss()
            }
        }
        if (addCancelButton) {
            val button = tooltip.addButton(cancelText, "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, confirmCancelButtonWidth, 25f, 0f)
            button.setShortcut(cancelButtonShortcut, true)
            button.position.inTL(xPos, 0f)

            cancelButton = button
            cancelButton!!.onClick {
                applyCancelScript()
                if (doesCancelDismiss)
                    dismiss()
            }
        }

        val bottom = goalHeight
        val alignX = when (alignment) {
            Alignment.LMID -> tooltipPadFromSide
            Alignment.MID -> 0f
            Alignment.RMID -> -tooltipPadFromSide
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
        panel.bringComponentToTop(ui)

        closeButton!!.onClick { dismiss() }
    }

    var headerTooltip: TooltipMakerAPI? = null
    protected fun createHeader() {
        if (headerTitle != null && headerTooltip == null) {
            val headerPad = 4f
            headerTooltip = panel.createUIElement(panel.position.width - (tooltipPadFromSide * 2f) + headerPad, 20f, false)
            headerTooltip!!.setParaFont(Fonts.ORBITRON_20AABOLD)
            val label = headerTooltip!!.addPara(headerTitle, Misc.getTooltipTitleAndLightHighlightColor(), 5f)
            panel.addUIElement(headerTooltip).inTL(tooltipPadFromSide - headerPad / 2, tooltipPadFromTop)
            val textWidth = label.computeTextWidth(label.text)
            label.position.setLocation(0f, 0f).inTL(((panel.position.width - (tooltipPadFromSide * 2) - headerPad) - textWidth) / 2f, 3f)
            tooltipPadFromTop += 30f
        }
    }
}