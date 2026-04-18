package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.getChildrenCopy
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.UIUtils.easeCubic
import fleetBuilder.util.api.CampaignUtils
import org.lwjgl.input.Keyboard
import java.awt.Color

open class ModalPanel : ComposablePanel() {

    init {
        background.alphaMult = 0.9f
    }

    enum class PanelAnimation {
        RESIZE_FADE,
        FADE_ONLY,
        NONE
    }

    open var animation = PanelAnimation.NONE

    open var openDuration = 0.15f
    open var closeDuration = 0.05f

    protected var anim = 0f
    protected var animDirection = 1f
    protected var closing = false
    protected var openAnimationFinished = false

    // Unset before init
    protected open var goalXOffset: Float = 0f
    protected open var goalYOffset: Float = 0f
    protected open var goalWidth: Float = 0f
    protected open var goalHeight: Float = 0f

    open var allowHotkeyQuit: Boolean = true
    open var hotkeyQuitConsumesInput: Boolean = true
    open var anyOuterMouseClickQuits: Boolean = false
    open var quitHotkeyClosesOnRelease: Boolean = false

    override var createUIOnInit: Boolean = false
    open var darkenBackground: Boolean = false
    open var darkenBackgroundAlphaMult: Float = 0.6f
    open var useCampaignDummyDialogAndPauseCombat: Boolean = false
    open var makeCampaignDummyDialogHideUI: Boolean = false
    protected open var successfullyOpenedCampaignDummyDialog: Boolean = false

    open var consumeAllEvents: Boolean = true

    override fun renderBelow(alphaMult: Float) {
        if (darkenBackground)
            UIUtils.darkenBackground(alphaMult * (alpha * darkenBackgroundAlphaMult), Color.BLACK)

        super.renderBelow(alphaMult)
    }

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

        if (useCampaignDummyDialogAndPauseCombat) {
            successfullyOpenedCampaignDummyDialog = CampaignUtils.openCampaignDummyDialog(makeCampaignDummyDialogHideUI)

            if (Global.getCurrentState() == GameState.COMBAT && Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused)
                Global.getCombatEngine().isPaused = true
        }

        if (openDuration == 0f || animation == PanelAnimation.NONE)
            setMaxSize()

        return panel
    }

    override fun applyExitScript() {
        if (useCampaignDummyDialogAndPauseCombat && successfullyOpenedCampaignDummyDialog)
            CampaignUtils.closeCampaignDummyDialog()

        super.applyExitScript()
    }

    override fun advance(amount: Float) {
        if (closing || !openAnimationFinished) {
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

        super.advance(amount)
    }

    protected open var escapeRequested: Boolean = false
    override fun processInput(events: MutableList<InputEventAPI>) {
        for (event in events) {
            if (event.isConsumed) continue

            if (allowHotkeyQuit) {
                val mouseEvent = if (!anyOuterMouseClickQuits) event.isRMBEvent else event.isMouseDownEvent
                val mouseDown = if (!anyOuterMouseClickQuits) event.isRMBDownEvent else event.isMouseDownEvent
                val mouseUp = if (!anyOuterMouseClickQuits) event.isRMBUpEvent else event.isRMBUpEvent

                if (quitHotkeyClosesOnRelease && escapeRequested) {
                    if (
                        (event.isKeyUpEvent && event.eventValue == Keyboard.KEY_ESCAPE) ||
                        (mouseUp && !UIUtils.isMouseHoveringOverComponent(panel, mouseX = event.x, mouseY = event.y, pad = inputCapturePad))
                    ) {
                        dismiss()
                        if (hotkeyQuitConsumesInput)
                            event.consume()
                        escapeRequested = false
                    } else if (
                        !(event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE) && // Escape not being held down?
                        !(mouseEvent) // RMB not being held down?
                    )
                        escapeRequested = true
                } else if (
                    (event.isKeyDownEvent && event.eventValue == Keyboard.KEY_ESCAPE) ||
                    (mouseDown && !UIUtils.isMouseHoveringOverComponent(panel, mouseX = event.x, mouseY = event.y, pad = inputCapturePad))
                ) {
                    if (quitHotkeyClosesOnRelease)
                        escapeRequested = true
                    else {
                        dismiss()
                        if (hotkeyQuitConsumesInput)
                            event.consume()
                    }
                }
            }
        }

        super.processInput(events)

        if (consumeAllEvents) {
            events.forEach { if (!it.isConsumed) it.consume() }
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

        if (closeDuration == 0f || animation == PanelAnimation.NONE)
            forceDismiss()
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