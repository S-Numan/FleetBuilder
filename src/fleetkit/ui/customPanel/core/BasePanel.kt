package fleetkit.ui.customPanel.core

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.SettingsAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetkit.core.util.DisplayMessage
import fleetkit.core.util.FTMisc.endStencil
import fleetkit.core.util.FTMisc.renderTiledTexture
import fleetkit.core.util.FTMisc.startStencilWithXPad
import fleetkit.core.util.FTMisc.startStencilWithYPad
import fleetkit.otherMods.starficz.*
import fleetkit.ui.UIUtils
import org.magiclib.util.reflection.UIFinder
import java.awt.Color

open class BasePanel : StarUIPanelPlugin() {
    var tooltip: TooltipMakerAPI? = null
        protected set

    protected open var createUIOnInit: Boolean = true

    override var consumeInnerMouseEvents: Boolean = true

    var hasInitOccurred = false
        private set

    @JvmOverloads
    open fun init(
        width: Float,
        height: Float,
        xOffset: Float = 0f,
        yOffset: Float = 0f,
        parent: UIPanelAPI? = UIFinder.getScreenPanel()
    ): CustomPanelAPI {
        val inputPanel = Global.getSettings().createCustom(width, height, this)

        if (hasInitOccurred) {
            DisplayMessage.showError("init already occurred")
            return inputPanel
        }
        if (parent == null) {
            DisplayMessage.showError("parent was null during init")
            return inputPanel
        }

        panel = inputPanel

        parent.addComponent(panel).inTL(xOffset, yOffset)
        parent.bringComponentToTop(panel)

        if (createUIOnInit)
            createUI()

        hasInitOccurred = true

        return panel
    }

    /** Typically the place to add ui elements to the panel */
    protected open fun createUI() {
    }

    /** Dismiss the panel immediately, skipping any animations */
    @JvmOverloads
    open fun forceDismiss(runExitScript: Boolean = true) {
        if (!hasInitOccurred) return

        if (runExitScript)
            applyExitScript()
        panel.parent?.removeComponent(panel)

        hasInitOccurred = false
    }

    private var exitCallback: (() -> Unit)? = null

    protected open fun applyExitScript() {
        exitCallback?.invoke()
        exitCallback = null
    }

    /** Set a callback to be run when the panel is dismissed */
    fun onExit(callback: () -> Unit) {
        exitCallback = callback
    }

    protected val settings: SettingsAPI = Global.getSettings()
    protected fun sprite(cat: String, id: String): SpriteAPI =
        settings.getSprite(cat, id)

    open val background: SpriteAPI = sprite("ui", "panel00_center").apply { color = Color.BLACK }
    open var renderUIBorders = true
    open var uiBorderColor: Color? = null

    open var renderBackground: Boolean = true
    open var dialogStyle: Boolean = false

    protected fun borderSprite(id: String) =
        sprite("ui", id).apply { setSize(16f, 16f) }

    open val bot = borderSprite("panel00_bot")
    open val top = borderSprite("panel00_top")
    open val left = borderSprite("panel00_left")
    open val right = borderSprite("panel00_right")
    open val topLeft = borderSprite("panel00_top_left")
    open val topRight = borderSprite("panel00_top_right")
    open val bottomLeft = borderSprite("panel00_bot_left")
    open val bottomRight = borderSprite("panel00_bot_right")

    override fun renderBelow(alphaMult: Float) {
        if (renderBackground) {
            renderTiledTexture(
                background.textureId, panel.x, panel.y, panel.width, panel.height,
                background.textureWidth, background.textureHeight,
                alphaMult * background.alphaMult, background.color
            )
        }

        renderBorders(alphaMult)

        super.renderBelow(alphaMult)
    }

    protected open fun renderBorders(alphaMult: Float) {
        if (!renderUIBorders)
            return

        if (!dialogStyle) {
            uiBorderColor?.let { UIUtils.renderUILines(panel, alphaMult, boxColor = it) } ?: UIUtils.renderUILines(panel, alphaMult)
            return
        }

        renderBorderSprite(alphaMult, top, bot, left, right, topLeft, topRight, bottomLeft, bottomRight)
    }

    protected fun renderBorderSprite(
        alphaMult: Float,
        top: SpriteAPI,
        bot: SpriteAPI,
        left: SpriteAPI,
        right: SpriteAPI,
        topLeft: SpriteAPI,
        topRight: SpriteAPI,
        bottomLeft: SpriteAPI,
        bottomRight: SpriteAPI
    ) {
        listOf(top, bot, left, right, topLeft, topRight, bottomLeft, bottomRight)
            .forEach {
                it.alphaMult = alphaMult
                if (uiBorderColor != null)
                    it.color = uiBorderColor
            }

        val leftX = panel.position.x + 16
        //val rightX = panel.getPosition().getX() + panel.getPosition().getWidth() - 16
        val botX = panel.y + 16
        startStencilWithXPad(panel, 8f)

        var i = leftX
        while (i <= panel.x + panel.width) {
            top.renderAtCenter(i, panel.y + panel.height)
            bot.renderAtCenter(i, panel.y)
            i += top.width
        }

        endStencil()
        startStencilWithYPad(panel, 8f)
        var q = botX
        while (q <= panel.y + panel.height) {
            left.renderAtCenter(panel.x, q)
            right.renderAtCenter(panel.x + panel.width, q)
            q += top.width
        }
        endStencil()
        topLeft.renderAtCenter(leftX - 16, panel.y + panel.height)
        topRight.renderAtCenter(panel.x + panel.width, panel.y + panel.height)
        bottomLeft.renderAtCenter(leftX - 16, panel.y)
        bottomRight.renderAtCenter(panel.x + panel.width, panel.y)
    }
}