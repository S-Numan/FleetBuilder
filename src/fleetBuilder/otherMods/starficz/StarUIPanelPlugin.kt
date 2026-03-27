package fleetBuilder.otherMods.starficz

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11

open class StarUIPanelPlugin(var panel: CustomPanelAPI) : BaseCustomUIPanelPlugin() {

    private var onClickFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onClickOutsideFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onClickReleaseFunctions: MutableList<(InputEventAPI) -> Unit> = ArrayList()
    private var onHoverFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onHoverEnterFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onHoverExitFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onMouseButtonHeldFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onKeyDownFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()
    private var onKeyUpFunctions: MutableList<(InputEventAPI) -> Unit> = mutableListOf()

    private var renderBelowFunctions: MutableList<(Float) -> Unit> = mutableListOf()
    private var renderFunctions: MutableList<(Float) -> Unit> = mutableListOf()

    private var advanceFunctions: MutableList<(Float) -> Unit> = mutableListOf()

    var customData: Any? = null

    var inputCaptureTopPad = 0f
        private set
    var inputCaptureBottomPad = 0f
        private set
    var inputCaptureLeftPad = 0f
        private set
    var inputCaptureRightPad = 0f
        private set

    var isHovering = false
        private set

    var hasClicked = false
        private set

    var consumeEvents = false
    var ignoreConsumedEvents = false

    fun setMouseCapturePad(leftPad: Float, rightPad: Float, topPad: Float, bottomPad: Float) {
        inputCaptureTopPad = topPad
        inputCaptureBottomPad = bottomPad
        inputCaptureLeftPad = leftPad
        inputCaptureRightPad = rightPad
        panel.setMouseOverPad(leftPad, rightPad, topPad, bottomPad)
    }

    fun renderBelow(function: (alphaMult: Float) -> Unit) {
        renderBelowFunctions.add(function)
    }

    override fun renderBelow(alphaMult: Float) {
        renderBelowFunctions.forEach {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            it(alphaMult)
            GL11.glPopMatrix()
        }
    }

    fun render(function: (alphaMult: Float) -> Unit) {
        renderFunctions.add(function)
    }

    override fun render(alphaMult: Float) {
        renderFunctions.forEach {
            GL11.glPushMatrix()
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            it(alphaMult)
            GL11.glPopMatrix()
        }
    }

    fun advance(function: (amount: Float) -> Unit) {
        advanceFunctions.add(function)
    }

    override fun advance(amount: Float) {
        advanceFunctions.forEach { it(amount) }
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        events.filter { it.isMouseEvent }.forEach { event ->
            if (ignoreConsumedEvents && event.isConsumed) return@forEach
            val inElement = event.x.toFloat() in (left - inputCaptureLeftPad)..(right + inputCaptureRightPad) &&
                    event.y.toFloat() in (bottom - inputCaptureBottomPad)..(top + inputCaptureTopPad)
            if (inElement) {
                onHoverFunctions.forEach { it(event) }
                if (!isHovering) onHoverEnterFunctions.forEach { it(event) }
                isHovering = true
                if (event.isMouseDownEvent) {
                    hasClicked = true
                    onClickFunctions.forEach { it(event) }
                }
                if (event.isMouseUpEvent && hasClicked) {
                    hasClicked = false
                    onClickReleaseFunctions.forEach { it(event) }
                }
                if (Mouse.isButtonDown(0)) onMouseButtonHeldFunctions.forEach { it(event) }
                if (consumeEvents) event.consume()
            } else {
                if (isHovering) onHoverExitFunctions.forEach { it(event) }
                isHovering = false
                if (event.isMouseDownEvent) {
                    onClickOutsideFunctions.forEach { it(event) }
                }
                if (event.isMouseUpEvent) {
                    hasClicked = false
                }
            }
        }

        events.filter { it.isKeyboardEvent }.forEach { event ->
            if (ignoreConsumedEvents && event.isConsumed) return@forEach
            if (event.isKeyDownEvent) onKeyDownFunctions.forEach { it(event) }
            if (event.isKeyUpEvent) onKeyUpFunctions.forEach { it(event) }
            if (consumeEvents) event.consume()
        }
    }

    fun onClick(function: (InputEventAPI) -> Unit) {
        onClickFunctions.add(function)
    }

    fun onClickRelease(function: (InputEventAPI) -> Unit) {
        onClickReleaseFunctions.add(function)
    }

    fun onClickOutside(function: (InputEventAPI) -> Unit) {
        onClickOutsideFunctions.add(function)
    }

    fun onHover(function: (InputEventAPI) -> Unit) {
        onHoverFunctions.add(function)
    }

    fun onHoverEnter(function: (InputEventAPI) -> Unit) {
        onHoverEnterFunctions.add(function)
    }

    fun onHoverExit(function: (InputEventAPI) -> Unit) {
        onHoverExitFunctions.add(function)
    }

    fun onHeld(function: (InputEventAPI) -> Unit) {
        onMouseButtonHeldFunctions.add(function)
    }

    fun onKeyDown(function: (InputEventAPI) -> Unit) {
        onKeyDownFunctions.add(function)
    }

    fun onKeyUp(function: (InputEventAPI) -> Unit) {
        onKeyUpFunctions.add(function)
    }

}