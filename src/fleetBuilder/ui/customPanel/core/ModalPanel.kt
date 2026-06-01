package fleetBuilder.ui.customPanel.core

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.otherMods.starficz.*
import fleetBuilder.ui.UIUtils
import fleetBuilder.ui.UIUtils.easeCubic
import fleetBuilder.ui.UIUtils.lerp
import fleetBuilder.ui.noise.UINoiseRenderer
import fleetBuilder.util.api.CampaignUtils
import org.lwjgl.input.Keyboard

open class ModalPanel : ComposablePanel() {

    enum class PanelAnimation {
        RESIZE_FADE,
        FADE_ONLY, // TODO: if fade in animation without resizing; Make all tooltip elements appear right away instead of when fade in is finished, and just eat all input if it's in the panel until the panel if fully open.
        NONE
    }
    // TODO: make a 'tooltip_fade_in_duration' whereas upon creating the tooltip, the tooltip opacity starts at 0 but slowly rises over the course of the 'tooltip_fade_in_duration'. This only takes effect for the RESIZE_FADE animation.
    //  Also add a 'tooltip_fade_out_duration', which prevents the closing animation from happening until the fade-out for the tooltip has occurred.
    //  These should both be extremely short. Like 0.07f and 0.02f respectively.
    //  If animation is equal to NONE, skip it.
    //  ^ This is roughly what vanilla does.

    open var animation = PanelAnimation.NONE

    open var openDuration = 0.15f
    open var closeDuration = 0.05f

    protected var anim = 0f
    protected var animDirection = 1f
    protected var closing = false
    protected var openAnimationFinished = false

    open var createUINoise: Boolean = false
    private var _noise: UINoiseRenderer? = null
    open val noise: UINoiseRenderer
        get() = _noise ?: UINoiseRenderer().also { _noise = it }

    // Unset before init
    protected open var goalXOffset: Float = 0f
    protected open var goalYOffset: Float = 0f
    protected open var goalWidth: Float = 0f
    protected open var goalHeight: Float = 0f

    open var allowHotkeyQuit: Boolean = true

    /**
     * Whether the hotkey to quit (Keyboard or Mouse) should consume input.
     */
    open var hotkeyQuitConsumesInput: Boolean = true
    open var anyOuterMouseClickQuits: Boolean = false
    open var quitHotkeyClosesOnRelease: Boolean = false

    override var createUIOnInit: Boolean = false
    open var darkenBackground: Boolean = false
    open var darkenBackgroundAlphaMult: Float = 0.6f
    protected open var currentDarkenBackgroundAlphaMult = darkenBackgroundAlphaMult
    open var useCampaignDummyDialogAndPauseCombat: Boolean = false
    open var makeCampaignDummyDialogHideUI: Boolean = false
    protected open var successfullyOpenedCampaignDummyDialog: Boolean = false

    open var consumeAllEvents: Boolean = true

    override fun renderBelow(alphaMult: Float) {
        if (darkenBackground)
            UIUtils.darkenBackground(currentDarkenBackgroundAlphaMult)

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

        if (createUINoise)
            noise.fadeInOut()

        return panel
    }

    override fun applyExitScript() {
        if (useCampaignDummyDialogAndPauseCombat && successfullyOpenedCampaignDummyDialog)
            CampaignUtils.closeCampaignDummyDialog()

        super.applyExitScript()
    }

    override fun advance(amount: Float) {
        if (createUINoise)
            noise.advance(amount)

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

    override fun render(alphaMult: Float) {
        super.render(alphaMult)
        if (createUINoise)
            noise.render(panel.x, panel.y, panel.width, panel.height, alphaMult)
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
                        (mouseUp && !UIUtils.isMouseHoveringOverComponent(panel, mouseX = event.x, mouseY = event.y, pad = mouseCapturePad))
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
                    (mouseDown && !UIUtils.isMouseHoveringOverComponent(panel, mouseX = event.x, mouseY = event.y, pad = mouseCapturePad))
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
        else {
            if (createUINoise)
                noise.fadeInOut(0f, outDuration = closeDuration * 4f)
        }
    }

    protected fun updatePanelVisuals(progress: Float) {
        if (panel.parent == null)
            return

        panel.opacity = progress
        currentDarkenBackgroundAlphaMult = lerp(0f, darkenBackgroundAlphaMult, progress)

        if (animation == PanelAnimation.FADE_ONLY) {
            panel.position?.setSize(goalWidth, goalHeight)
            panel.position?.inTL(goalXOffset, panel.parent!!.height - goalYOffset)
            return
        }

        val currentHeight = goalHeight * progress

        val centerY = panel.parent!!.height - goalYOffset
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