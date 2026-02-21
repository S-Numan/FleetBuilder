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
import fleetBuilder.ui.customPanel.DialogUtil
import fleetBuilder.util.FBMisc.renderTiledTexture
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.UIUtils
import org.lwjgl.opengl.GL11
import starficz.*
import java.awt.Color

//Copied and heavily modified from AshLib

open class CustomUIPanel : CustomUIPanelPlugin {
    open var layoutOffsetX: Float = 0f
    open var layoutOffsetY: Float = 0f

    open lateinit var parent: UIPanelAPI
    open lateinit var panel: CustomPanelAPI

    open var consumeMouseEvents: Boolean = true

    open var createUIOnInit: Boolean = true


    open var currAlpha: Float = 1f
    open val rendererBorder: UILinesRenderer = UILinesRenderer(0f)

    private val settings = Global.getSettings()

    protected fun sprite(cat: String, id: String): SpriteAPI =
        settings.getSprite(cat, id)

    open val panelBackground = sprite("ui", "panel00_center")
    open var panelBackgroundAlphaMult: Float = 1f

    open fun init(
        width: Float,
        height: Float,
        xPos: Float,
        yPos: Float,
        parent: UIPanelAPI? = ReflectionMisc.getScreenPanel()
    ): CustomPanelAPI {
        panel = Global.getSettings().createCustom(width, height, this)

        this.parent = parent ?: run {
            DisplayMessage.showError("parent was null when creating dialog")
            return panel
        }

        parent.addComponent(panel).inTL(xPos, parent.position.height - yPos)
        parent.bringComponentToTop(panel)

        rendererBorder.setPanel(panel)

        if (createUIOnInit)
            createUI()

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

    open fun forceDismiss() {
        parent.removeComponent(panel)
        applyExitScript()
    }

    open fun forceDismissNoExit() {
        parent.removeComponent(panel)
    }

    private var exitCallback: (() -> Unit)? = null

    open fun applyExitScript() {
        exitCallback?.invoke()
    }

    fun onExit(callback: () -> Unit) {
        exitCallback = callback
    }

    override fun positionChanged(position: PositionAPI?) {
    }

    override fun buttonPressed(buttonId: Any?) {
    }

    override fun render(alphaMult: Float) {
    }

    override fun renderBelow(alphaMult: Float) {
        renderTiledTexture(
            panelBackground.textureId,
            panel.x,
            panel.y, panel.width,
            panel.height, panelBackground.textureWidth,
            panelBackground.textureHeight, currAlpha * panelBackgroundAlphaMult, panelBackground.color
        )

        rendererBorder.render(alphaMult)
    }
}