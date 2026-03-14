package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.UIUtils.easeCubic
import fleetBuilder.util.api.CampaignUtils
import org.lwjgl.input.Keyboard
import starficz.getChildrenCopy
import starficz.height

open class PopUpPanel : ComposableUIPanel() {

    init {
        background.alphaMult = 0.9f
    }

    enum class PanelAnimation {
        RESIZE_FADE,
        FADE_ONLY
    }

    open var animation = PanelAnimation.RESIZE_FADE

    open var openDuration = 0.15f
    open var closeDuration = 0.05f

    protected var anim = 0f
    protected var animDirection = 1f
    protected var closing = false
    protected var openAnimationFinished = false

    // Unset before init
    open var goalXOffset: Float = 0f
    open var goalYOffset: Float = 0f
    open var goalWidth: Float = 0f
    open var goalHeight: Float = 0f

    open var consumeAllInput: Boolean = true
    open var allowHotkeyQuit: Boolean = true

    open var attemptedExit: Boolean = false

    override var dialogStyle: Boolean = true
    override var xTooltipPad = 10f
    override var yTooltipPad = 10f
    override var createUIOnInit: Boolean = false
    override var darkenBackground: Boolean = true

    override fun init(
        width: Float,
        height: Float,
        xOffset: Float,
        yOffset: Float,
        parent: UIPanelAPI?
    ): CustomPanelAPI {
        goalWidth = width
        goalHeight = height
        goalXOffset = xOffset
        goalYOffset = yOffset

        super.init(width, height, xOffset, yOffset, parent)

        CampaignUtils.openCampaignDummyDialog()

        if (Global.getCurrentState() == GameState.COMBAT && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
            Global.getCombatEngine().isPaused = true

        return panel
    }

    override fun applyExitScript() {
        CampaignUtils.closeCampaignDummyDialog()
        super.applyExitScript()
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        if (!closing && openAnimationFinished)
            return // Panel opened and not closing, do not continue.

        val duration = if (closing) closeDuration else openDuration
        val rate = amount / duration

        anim += rate * animDirection
        anim = anim.coerceIn(0f, 1f)

        val eased = easeCubic(anim)

        updatePanelVisuals(eased)

        if (!closing && anim >= 1f)
            setMaxSize()

        if (closing && anim <= 0f)
            forceDismiss()
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        super.processInput(events)

        for (event in events) {
            if (event.isConsumed) continue

            if (allowHotkeyQuit &&
                (event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) ||
                (event.isRMBEvent && !UIUtils.isMouseHoveringOverComponent(panel, 4f))
            ) {

                if (attemptedExit) {
                    dismiss()
                    event.consume()
                } else {
                    attemptedExit = true
                    event.consume()
                }
            }

            if (consumeAllInput)
                event.consume()
        }
    }

    @JvmOverloads
    open fun dismiss(animation: PanelAnimation = this.animation) {
        if (closing) return

        this.animation = animation
        closing = true
        animDirection = -1f

        panel.getChildrenCopy().forEach {
            panel.removeComponent(it)
        }
    }

    protected fun updatePanelVisuals(progress: Float) {
        alpha = progress

        if (animation == PanelAnimation.FADE_ONLY) {
            panel.position?.setSize(goalWidth, goalHeight)
            panel.position?.inTL(goalXOffset, parent.height - goalYOffset)
            return
        }

        val currentHeight = goalHeight * progress

        val centerY = parent.height - goalYOffset
        val topLeftY = centerY + (goalHeight / 2f) - (currentHeight / 2f)

        panel.position?.setSize(goalWidth, currentHeight)
        panel.position?.inTL(goalXOffset, topLeftY)
    }

    fun setMaxSize() {
        openAnimationFinished = true
        anim = 1f
        updatePanelVisuals(1f)
        createUI()
    }
}