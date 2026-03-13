package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.util.api.CampaignUtils
import fleetBuilder.util.api.UIUtils
import org.lwjgl.input.Keyboard

open class PopUpPanel : ComposableUIPanel() {

    init {
        background.alphaMult = 0.9f
    }

    open var goalWidth: Float = 0f // Unset before init
    open var goalHeight: Float = 0f // Unset before init
    open var openDuration = 0.05f
    private var elapsed = 0f

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

        super.init(width = width, height = height, xOffset = xOffset, yOffset = yOffset, parent = parent)

        CampaignUtils.openCampaignDummyDialog() // Only does so if in the campaign

        //if (Global.getCurrentState() == GameState.CAMPAIGN && !Global.getSector().isPaused)
        //    Global.getSector().isPaused = true

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

        if (!reachedMaxHeight) {
            elapsed += amount
            val progress = (elapsed / openDuration).coerceIn(0f, 1f)
            alpha = progress

            panel.position?.setSize(goalWidth, goalHeight * progress)

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
                (event.isKeyboardEvent && event.eventValue == Keyboard.KEY_ESCAPE)
                || (event.isRMBEvent && !UIUtils.isMouseHoveringOverComponent(panel, 4f))
            ) {
                if (attemptedExit) {
                    forceDismiss()
                    event.consume()
                } else {
                    attemptedExit = true
                    event.consume()
                }
            }
            //}
            if (consumeAllInput)
                event.consume()
        }
    }

    fun setMaxSize() {
        reachedMaxHeight = true
        panel.position.setSize(goalWidth, goalHeight)
        alpha = 1f
        createUI()
    }
}