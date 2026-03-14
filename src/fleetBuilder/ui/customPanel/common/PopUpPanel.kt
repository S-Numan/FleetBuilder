package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.ui.UIUtils
import fleetBuilder.util.api.CampaignUtils
import org.lwjgl.input.Keyboard
import starficz.getChildrenCopy
import starficz.height

open class PopUpPanel : ComposableUIPanel() {

    init {
        background.alphaMult = 0.9f
    }

    enum class CloseAnimation {
        SHRINK_FADE,
        FADE_ONLY
    }

    open var closeAnimation = CloseAnimation.SHRINK_FADE
    open var closeDuration = 01.1f

    protected open var closing = false
    protected var closeElapsed = 0f

    // Unset before init
    open var goalXOffset: Float = 0f
    open var goalYOffset: Float = 0f
    open var goalWidth: Float = 0f
    open var goalHeight: Float = 0f

    open var openDuration = 01.05f

    protected open var elapsed = 0f

    open var consumeAllInput: Boolean = true
    open var allowHotkeyQuit: Boolean = true

    open var attemptedExit: Boolean = false
    open var reachedMaxHeight: Boolean = false

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

        if (elapsed >= openDuration)
            setMaxSize()

        return panel
    }

    override fun applyExitScript() {
        CampaignUtils.closeCampaignDummyDialog()
        super.applyExitScript()
    }

    override fun advance(amount: Float) {
        super.advance(amount)

        if (closing) {
            updateClosing(amount)
            return
        }

        if (!reachedMaxHeight) {
            elapsed += amount
            val progress = (elapsed / openDuration).coerceIn(0f, 1f)
            alpha = progress

            val currentHeight = goalHeight * progress
            val centerY = parent.height - goalYOffset
            val topLeftY = centerY + (goalHeight / 2f) - (currentHeight / 2f)

            panel.position?.setSize(goalWidth, currentHeight)
            panel.position?.inTL(goalXOffset, topLeftY)

            if (progress == 1f) {
                setMaxSize()
            }
        }
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

    open fun dismiss(animation: CloseAnimation = closeAnimation) {
        if (closing) return
        closeAnimation = animation
        closing = true
        closeElapsed = 0f
        panel.getChildrenCopy().forEach { panel.removeComponent(it) }
    }

    private fun updateClosing(amount: Float) {
        closeElapsed += amount

        val progress = (closeElapsed / closeDuration).coerceIn(0f, 1f)
        val inv = 1f - progress

        when (closeAnimation) {
            CloseAnimation.SHRINK_FADE -> {
                alpha = inv

                val currentHeight = goalHeight * inv
                val centerY = parent.height - goalYOffset
                val topLeftY = centerY + (goalHeight / 2f) - (currentHeight / 2f)

                panel.position?.setSize(goalWidth, currentHeight)
                panel.position?.inTL(goalXOffset, topLeftY)
            }

            CloseAnimation.FADE_ONLY -> {
                alpha = inv
            }
        }

        if (progress >= 1f) {
            forceDismiss()
        }
    }

    fun setMaxSize() {
        reachedMaxHeight = true
        panel.position?.setSize(goalWidth, goalHeight)
        panel.position?.inTL(goalXOffset, parent.height - goalYOffset)
        alpha = 1f
        createUI()
    }
}