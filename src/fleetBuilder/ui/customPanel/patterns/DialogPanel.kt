package fleetBuilder.ui.customPanel.patterns

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.util.FBTxt
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.core.ModalPanel
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Color

open class DialogPanel(
    open var headerTitle: String? = null
) : ModalPanel() {

    init {
        //background.alphaMult = 0.95f
    }

    var confirmButton: ButtonAPI? = null
    var cancelButton: ButtonAPI? = null
    var closeButton: ButtonAPI? = null

    var confirmCancelButtonWidth: Float = 160f

    var doesConfirmDismiss: Boolean = true
    var doesCancelDismiss: Boolean = true

    var confirmButtonShortcut = Keyboard.KEY_G
    var cancelButtonShortcut = Keyboard.KEY_ESCAPE

    override var dialogStyle: Boolean = true
    override var tooltipPadFromSide = 12f
    override var tooltipPadFromTop = 10f
    override var tooltipPadFromBottom = 10f

    override var darkenBackground: Boolean = true
    override var useCampaignDummyDialogAndPauseCombat = true

    override fun createUI() {
        createHeader()

        super.createUI()
    }

    override fun renderBelow(alphaMult: Float) {
        val previousRenderUIBorders = renderUIBorders
        renderUIBorders = false

        super.renderBelow(alphaMult)

        var innerBorderRendered = false
        if (previousRenderUIBorders && renderInnerUIBorders && dialogStyle) {
            renderBorderSprite(alphaMult, top_inner, bot_inner, left_inner, right_inner, topLeft_inner, topRight_inner, bottomLeft_inner, bottomRight_inner)
            innerBorderRendered = true
        }

        if (!closing) {
            headerTooltip?.let { tooltip ->
                UIUtils.drawRectangleFilledForTooltip(
                    tooltip,
                    alphaMult,
                    headerColor
                )
            }
        }

        if (previousRenderUIBorders) {
            renderUIBorders = true

            if (innerBorderRendered)
                renderInnerUIBorders = false

            renderBorders(alphaMult)
            if (innerBorderRendered)
                renderInnerUIBorders = true
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
            val button = tooltip.addButton(confirmText, "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, confirmCancelButtonWidth, 25f, 0f)
            button.setShortcut(confirmButtonShortcut, true)
            button.position.inTL(xPos, 0f)
            xPos += confirmCancelButtonWidth + spacing

            button.onClick {
                applyConfirmScript()
                if (doesConfirmDismiss)
                    dismiss()
            }
            confirmButton = button
        }
        if (addCancelButton) {
            val button = tooltip.addButton(cancelText, "cancel", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, confirmCancelButtonWidth, 25f, 0f)
            button.setShortcut(cancelButtonShortcut, true)
            button.position.inTL(xPos, 0f)

            button.onClick {
                applyCancelScript()
                if (doesCancelDismiss)
                    dismiss()
            }
            cancelButton = button
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

    fun addCloseButton(closeButtonSize: Float = 28f) {
        val ui = panel.createUIElement(closeButtonSize, closeButtonSize, false)

        ui.setButtonFontOrbitron20Bold()
        // TODO, custom button functionality to avoid button background. Only the red X is needed here. (maybe a red outline on hover, but that's it)
        val closeButton = ui.addButton(
            "X",
            "close_button",
            Color.RED.darker(),
            headerColor,
            Alignment.MID,
            CutStyle.NONE,
            closeButtonSize,
            closeButtonSize,
            0f
        )

        // Position in top-right
        panel.addUIElement(ui).inTR(5f, 0f)
        panel.bringComponentToTop(ui)

        closeButton!!.onClick { dismiss() }

        this.closeButton = closeButton
    }

    var headerTooltip: TooltipMakerAPI? = null
    var headerHeight = 28f
    var headerPadFromTop = 0f
    var headerPadFromSide = 0f
    var headerColor: Color = Global.getSettings().getColor("buttonBgDark").darker() //Global.getSector().playerFaction.darkUIColor.darker()
    protected fun createHeader() {
        if (headerTitle != null && headerTooltip == null) {
            val headerSidePad =
                if (dialogStyle) 0f
                else 0f
            val headerExtraTopPad =
                if (dialogStyle) 0f
                else 0f

            val headerTooltip = panel.createUIElement(panel.position.width - (headerPadFromSide * 2f) + headerSidePad, headerHeight, false)
            panel.addUIElement(headerTooltip).inTL(headerPadFromSide - headerSidePad / 2, headerPadFromTop)

            headerTooltip.setParaFont(Fonts.ORBITRON_20AABOLD)
            val label = headerTooltip.addPara(headerTitle, Misc.getTooltipTitleAndLightHighlightColor(), 0f)
            val textWidth = label.computeTextWidth(label.text)
            val textHeight = label.computeTextHeight(label.text)
            label.position.inTL(((panel.position.width - (headerPadFromSide * 2) - headerSidePad) - textWidth) / 2f, (headerHeight - textHeight) / 2f)

            tooltipPadFromTop += headerHeight + headerExtraTopPad
            this.headerTooltip = headerTooltip
        }
    }
}