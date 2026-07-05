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
import kotlin.math.max

open class ModalPanel : ComposablePanel() {

    enum class PanelAnimation {
        RESIZE_FADE,
        FADE_ONLY,
        NONE
    }

    /** The type of animation to use when opening and closing the panel */
    open var animation = PanelAnimation.NONE

    /** The duration the open animation lasts */
    open var openDuration = 0.25f

    /** The duration the close animation lasts */
    open var closeDuration = 0.10f

    /** The point at which the resize animation is finished and only fade in is left for the animation. Only applies to [PanelAnimation.RESIZE_FADE] on panel opening */
    open var resizeFinishPoint = 0.4f

    /** Whether to ease the open animation speed instead of a more linear opening speed */
    var easeOpenAnimation = false

    /** The current animation progress*/
    protected var anim = 0f

    /** Whether the panel is currently closing */
    protected var closing = false

    /** Whether the open animation has finished */
    protected var openAnimationFinished = false

    /** Whether to create a UI noise effect on dialog open and close as often seen in the base-game */
    open var createUINoise: Boolean = false

    /** Whether to create a UI noise effect when clicking outside the panel. Only applies if [createUINoise] is also true. */
    open var createUINoiseOnClickOutside: Boolean = true
    private var _noise: UINoiseRenderer? = null
    open val noise: UINoiseRenderer
        get() = _noise ?: UINoiseRenderer().also { _noise = it }

    // Unset before init
    protected open var goalXOffset: Float = 0f
    protected open var goalYOffset: Float = 0f
    protected open var goalWidth: Float = 0f
    protected open var goalHeight: Float = 0f

    /** Whether the quit hotkey (escape key or right click outside the panel) is enabled. */
    open var allowHotkeyQuit: Boolean = true

    /** Whether the quit hotkey (escape key or right click outside the panel) should consume input. Only applies if [allowHotkeyQuit] is also true. */
    open var hotkeyQuitConsumesInput: Boolean = true

    /** Whether any mouse click outside the panel should quit the panel, as opposed to only the right mouse button. Only applies if [allowHotkeyQuit] is also true. */
    open var anyOuterMouseClickQuits: Boolean = false
    open var quitHotkeyClosesOnRelease: Boolean = false

    override var createUIOnInit: Boolean = false

    /** Whether to darken the background while the panel is open. */
    open var darkenBackground: Boolean = false
    open var darkenBackgroundAlphaMult: Float = 0.6f
    protected open var currentDarkenBackgroundAlphaMult = darkenBackgroundAlphaMult

    /** Whether to create a campaign dummy dialog (which pauses the campaign) or pause combat while the panel is open. */
    open var useCampaignDummyDialogAndPauseCombat: Boolean = false
    open var makeCampaignDummyDialogHideUI: Boolean = false
    protected open var successfullyOpenedCampaignDummyDialog: Boolean = false

    /** Whether to consume all input events while the panel is open. */
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
            finishAnimation()

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

            anim += rate * if (closing) -1f else 1f
            anim = anim.coerceIn(0f, 1f)

            if (easeOpenAnimation)
                updatePanelVisuals(easeCubic(anim))
            else
                updatePanelVisuals(anim)


            if (!closing && anim >= 1f)
                finishAnimation()

            if (closing && anim <= 0f)
                forceDismiss()
        }

        super.advance(amount)
    }

    override fun render(alphaMult: Float) {
        super.render(alphaMult)
        if (createUINoise)
            noise.render(panel.x, panel.y, panel.width, panel.height, max(alphaMult, 0.5f))
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

            if (!event.isConsumed && !closing && createUINoise && createUINoiseOnClickOutside) {
                if (event.isMouseDownEvent && !UIUtils.isMouseHoveringOverComponent(panel, mouseX = event.x, mouseY = event.y, pad = mouseCapturePad))
                    noise.fadeInOut(noise.defaultInDuration / 2f, noise.defaultOutDuration / 2f)
            }
        }

        super.processInput(events)

        if (consumeAllEvents) {
            events.forEach { if (!it.isConsumed) it.consume() }
        }
    }

    /** Dismisses the panel with the preset animation, if present
     *
     * Also creates UI noise if enabled.
     */
    @JvmOverloads
    open fun dismiss(animation: PanelAnimation = this.animation) {
        if (closing) return

        this.animation = animation
        closing = true

        panel.getChildrenCopy().forEach {
            panel.removeComponent(it)
        }

        if (closeDuration == 0f || animation == PanelAnimation.NONE) {
            forceDismiss()
        } else {
            if (createUINoise)
                noise.fadeInOut(0f, outDuration = closeDuration * 4f)
        }
    }

    protected fun updatePanelVisuals(progress: Float) {
        val parent = panel.parent ?: return

        panel.opacity = progress
        currentDarkenBackgroundAlphaMult = lerp(0f, darkenBackgroundAlphaMult, progress)

        if (animation == PanelAnimation.FADE_ONLY) {
            panel.position?.setSize(goalWidth, goalHeight)
            panel.position?.inTL(goalXOffset, parent.height - goalYOffset)

            if (tooltip == null)
                createUI()
            return
        }

        val resizeProgress =
            if (!closing) (progress / resizeFinishPoint).coerceIn(0f, 1f)
            else progress

        val currentHeight = goalHeight * resizeProgress

        val centerY = parent.height - goalYOffset
        val topLeftY = centerY + (goalHeight / 2f) - (currentHeight / 2f)

        panel.position?.setSize(goalWidth, currentHeight)
        panel.position?.inTL(goalXOffset, topLeftY)

        if (currentHeight == goalHeight && tooltip == null)
            createUI()
    }

    fun finishAnimation() {
        openAnimationFinished = true
        anim = 1f
        updatePanelVisuals(1f)
        if (tooltip == null)
            createUI()
    }
}