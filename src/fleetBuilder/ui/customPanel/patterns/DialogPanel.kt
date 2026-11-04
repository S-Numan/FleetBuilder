package fleetBuilder.ui.customPanel.patterns

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.util.FBTxt
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.customPanel.core.ModalPanel
import fleetBuilder.ui.customPanel.elements.UIButton
import org.lwjgl.input.Keyboard
import java.awt.Color

open class DialogPanel(
    open var headerTitle: String? = null
) : ModalPanel() {

    init {
        background.alphaMult = 0.95f
    }

    var confirmButton: ButtonAPI? = null
    var cancelButton: ButtonAPI? = null
    internal var closeButton: UIButton? = null

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

    //override var animation = PanelAnimation.RESIZE_FADE
    //override var createUINoise = true

    override fun createUI() {
        createHeader()

        super.createUI()
    }

    protected open fun borderSpriteFB(id: String) =
        sprite("FleetBuilder", id).apply { setSize(16f, 16f) }

    open val bot_cut = borderSpriteFB("panel00_bot_cut")
    open val top_cut = borderSpriteFB("panel00_top_cut")
    open val left_cut = borderSpriteFB("panel00_left_cut")
    open val right_cut = borderSpriteFB("panel00_right_cut")
    open val topLeft_cut = borderSpriteFB("panel00_top_left_cut")
    open val topRight_cut = borderSpriteFB("panel00_top_right_cut")
    open val bottomLeft_cut = borderSpriteFB("panel00_bot_left_cut")
    open val bottomRight_cut = borderSpriteFB("panel00_bot_right_cut")

    /*
    open val bot_inner = borderSpriteFB("panel00_bot_inner")
    open val top_inner = borderSpriteFB("panel00_top_inner")
    open val left_inner = borderSpriteFB("panel00_left_inner")
    open val right_inner = borderSpriteFB("panel00_right_inner")
    open val topLeft_inner = borderSpriteFB("panel00_top_left_inner")
    open val topRight_inner = borderSpriteFB("panel00_top_right_inner")
    open val bottomLeft_inner = borderSpriteFB("panel00_bot_left_inner")
    open val bottomRight_inner = borderSpriteFB("panel00_bot_right_inner")
    */

    override fun renderBelow(alphaMult: Float) {
        super.renderBelow(alphaMult)

        if (!closing) {
            headerTooltip?.let { tooltip ->
                UIUtils.drawRectangleFilledForTooltip(
                    tooltip,
                    alphaMult,
                    headerColor
                )
            }
        }

        // Render inner borders again to overwrite parts that clip into the panel.
        if (renderUIBorders && dialogStyle)
            renderBorderSprite(alphaMult, top_cut, bot_cut, left_cut, right_cut, topLeft_cut, topRight_cut, bottomLeft_cut, bottomRight_cut)
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

    fun addCloseButton(
        closeButtonSize: Float = 26f,
        closeButtonSpacer: Float = 2f,
        defaultColor: Color = Color.RED.darker()
    ) {
        val ui = panel.createUIElement(closeButtonSize, closeButtonSize, false)

        val closeButton = UIButton(closeButtonSize, closeButtonSize)
        val defaultCheck = Global.getSettings().getSprite("ui", "checkmark_x")
        defaultCheck.color = defaultColor
        closeButton.setSprite(defaultCheck)
        closeButton.triggerOnPress = false
        closeButton.renderBackground = false

        ui.addCustom(closeButton.panel, 0f)

        // Position in top-right
        panel.addUIElement(ui).inTR(5f + closeButtonSpacer, 0f + closeButtonSpacer)
        panel.bringComponentToTop(ui)

        closeButton.onTrigger { dismiss() }

        this.closeButton = closeButton
    }

    var headerTooltip: TooltipMakerAPI? = null
    var headerHeight = 28f
    var headerPadFromTop = 0f
    var headerPadFromSide = 0f
    var headerColor: Color = Global.getSettings().getColor("buttonBgDark").darker().darker() //Global.getSector().playerFaction.darkUIColor.darker()
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