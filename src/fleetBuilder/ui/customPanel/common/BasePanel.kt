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
import fleetBuilder.otherMods.starficz.height
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.otherMods.starficz.x
import fleetBuilder.otherMods.starficz.y
import fleetBuilder.ui.UIUtils
import fleetBuilder.util.FBMisc.renderTiledTexture
import fleetBuilder.util.ReflectionMisc
import java.awt.Color

//Copied and heavily modified from AshLib

open class BasePanel : CustomUIPanelPlugin {
    lateinit var parent: UIPanelAPI
        protected set
    lateinit var panel: CustomPanelAPI
        protected set
    var tooltip: TooltipMakerAPI? = null
        protected set

    open var consumeMouseEvents: Boolean = true
    protected open var createUIOnInit: Boolean = true

    protected open var alpha: Float = 1f

    private val settings = Global.getSettings()
    protected fun sprite(cat: String, id: String): SpriteAPI =
        settings.getSprite(cat, id)

    open val background by lazy { sprite("ui", "panel00_center").apply { color = Color.BLACK } }

    open var renderUIBorders = true
    open var uiBorderColor: Color? = null

    var initOccured = false
        private set

    @JvmOverloads
    open fun init(
        width: Float,
        height: Float,
        xOffset: Float = 0f,
        yOffset: Float = 0f,
        parent: UIPanelAPI? = ReflectionMisc.getScreenPanel()
    ): CustomPanelAPI {
        val inputPanel = Global.getSettings().createCustom(width, height, this)

        if (initOccured) {
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

        if (createUIOnInit)
            createUI()

        initOccured = true

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

    @JvmOverloads
    open fun forceDismiss(runExitScript: Boolean = true) {
        if (!initOccured) return

        parent.removeComponent(panel)
        if (runExitScript)
            applyExitScript()

        initOccured = false
    }

    private var exitCallback: (() -> Unit)? = null

    protected open fun applyExitScript() {
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
            background.textureHeight, alpha * background.alphaMult, background.color
        )

        if (renderUIBorders) {
            uiBorderColor?.let { UIUtils.renderUILines(panel, alphaMult, boxColor = it) }
                ?: UIUtils.renderUILines(panel, alphaMult)

        }
    }

    override fun positionChanged(position: PositionAPI?) {
    }

    override fun buttonPressed(buttonId: Any?) {
    }

    override fun render(alphaMult: Float) {
    }
}