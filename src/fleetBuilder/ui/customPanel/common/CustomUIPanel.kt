package fleetBuilder.ui.customPanel.common

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.util.FBMisc.renderTiledTexture
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.UIUtils
import starficz.height
import starficz.width
import starficz.x
import starficz.y

//Copied and heavily modified from AshLib

open class CustomUIPanel : CustomUIPanelPlugin {
    lateinit var parent: UIPanelAPI
        protected set
    lateinit var panel: CustomPanelAPI
        protected set
    lateinit var tooltip: TooltipMakerAPI
        protected set

    open var consumeMouseEvents: Boolean = true
    protected open var createUIOnInit: Boolean = true

    open var alpha: Float = 1f
    open val rendererBorder: UILinesRendererGL = UILinesRendererGL(0f)

    private val settings = Global.getSettings()

    protected fun sprite(cat: String, id: String): SpriteAPI =
        settings.getSprite(cat, id)

    open val background = sprite("ui", "panel00_center")
    open var backgroundAlphaMult: Float = 1f

    var isOpen = false
        private set

    open fun init(
        width: Float,
        height: Float,
        xOffset: Float,
        yOffset: Float,
        parent: UIPanelAPI? = ReflectionMisc.getScreenPanel()
    ): CustomPanelAPI {
        val inputPanel = Global.getSettings().createCustom(width, height, this)

        if (isOpen) {
            DisplayMessage.showError("init already occurred")
            return inputPanel
        }
        if (parent == null) {
            DisplayMessage.showError("parent was null during init")
            return inputPanel
        }

        panel = inputPanel

        this.parent = parent

        parent.addComponent(panel).inTL(xOffset, parent.height - yOffset)
        parent.bringComponentToTop(panel)

        rendererBorder.setPanel(panel)

        if (createUIOnInit)
            createUI()

        isOpen = true

        return panel
    }

    open fun createUI() {
    }

    override fun advance(amount: Float) {
    }

    override fun processInput(events: MutableList<InputEventAPI>) {
        for (event in events) {
            if (event.isConsumed) continue

            val hovers = UIUtils.isMouseWithinBounds(panel.x, panel.y, panel.width, panel.height)

            if (hovers && consumeMouseEvents && event.isMouseEvent)
                event.consume()
        }
    }

    open fun forceDismiss(runExitScript: Boolean = true) {
        if (!isOpen) return

        parent.removeComponent(panel)
        if (runExitScript)
            applyExitScript()

        isOpen = false
    }

    private var exitCallback: (() -> Unit)? = null

    open fun applyExitScript() {
        exitCallback?.invoke()
        exitCallback = null
    }

    fun onExit(callback: () -> Unit) {
        exitCallback = callback
    }

    override fun renderBelow(alphaMult: Float) {
        renderTiledTexture(
            background.textureId,
            panel.x,
            panel.y, panel.width,
            panel.height, background.textureWidth,
            background.textureHeight, alpha * backgroundAlphaMult, background.color
        )

        rendererBorder.render(alphaMult)
    }

    override fun positionChanged(position: PositionAPI?) {
    }

    override fun buttonPressed(buttonId: Any?) {
    }

    override fun render(alphaMult: Float) {
    }
}