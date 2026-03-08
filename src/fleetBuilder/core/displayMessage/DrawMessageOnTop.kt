package fleetBuilder.core.displayMessage

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.GameState
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.util.ReflectionMisc
import org.lazywizard.lazylib.ui.FontException
import org.lazywizard.lazylib.ui.LazyFont
import org.magiclib.kotlin.setAlpha
import starficz.lastComponent
import java.awt.Color

// TODO, add color capability.

class DrawMessageOnTop : EveryFrameCombatPlugin, EveryFrameScript {

    companion object {
        private val messageQueue = ArrayDeque<Pair<String, Color>>()
        private var currentMessage: Pair<String, Color>? = null
        private var messageTimer = 0f
        private var font: LazyFont? = null
        private var toDraw: LazyFont.DrawableString? = null

        private const val TOP_BUFFER = 10f

        private var init = false
        private var fadingOut = false
        private var curState = GameState.TITLE

        // Config
        private const val DISPLAY_TIME = 5f         // seconds before fade starts (no other message)
        private const val FADE_DURATION = 0.5f        // fade-out time in seconds
        private const val QUICK_DISPLAY_TIME = 0.25f // time before fade starts if another message is waiting

        /** Call this to queue a message */
        fun addMessage(text: String, color: Color) {
            messageQueue.addLast(text to color)
        }
    }

    private fun doInit() {
        if (font == null) {
            try {
                font = LazyFont.loadFont("graphics/fonts/orbitron24aabold.fnt")
            } catch (e: FontException) {
                Global.getLogger(this.javaClass).error("Failed to load font", e)
                return
            }
        }

        init = true
    }

    override fun isDone(): Boolean = false
    override fun runWhilePaused(): Boolean = true
    override fun advance(amount: Float) {
        stateChangeChecker()
        if (curState != GameState.CAMPAIGN)
            return

        advanceAmount(amount)
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        stateChangeChecker()
        if (curState != GameState.TITLE && curState != GameState.COMBAT)
            return

        advanceAmount(amount)
    }

    private fun stateChangeChecker() {
        val state = Global.getCurrentState()
        if (state != curState) {
            if (toDraw != null) {
                currentMessage = null
                toDraw = null
                messageQueue.clear()
                messageTimer = 0f
                fadingOut = false
            }
            curState = state

            val screenPanel = ReflectionMisc.getScreenPanel() ?: return

            class CampaignMessageRenderer : BaseCustomUIPanelPlugin() {
                var panel: CustomPanelAPI

                init {
                    val settings = Global.getSettings()
                    panel = settings.createCustom(settings.screenWidth, settings.screenHeight, this)

                    screenPanel.addComponent(panel).inTL(0f, 0f)
                }

                override fun render(alphaMult: Float) {
                    render()
                }

                override fun advance(amount: Float) {
                    if ((screenPanel.lastComponent as? CustomPanelAPI)?.plugin != this)
                        screenPanel.bringComponentToTop(panel)
                }
            }
            if (state == GameState.CAMPAIGN) {
                CampaignMessageRenderer()
            }// else {
            //   val messageRender = screenPanel.findChildWithPlugin(CampaignMessageRenderer::class.java)
            //    if (messageRender != null)
            //       screenPanel.removeComponent(messageRender)
            //}
        }
    }


    private fun advanceAmount(amount: Float) {
        if (!init)
            doInit()

        // Start new message if none is active
        if (currentMessage == null && messageQueue.isNotEmpty()) {
            showNextMessage()
        }

        // Update current message
        if (currentMessage != null) {
            messageTimer += amount

            val limit = if (messageQueue.isNotEmpty()) QUICK_DISPLAY_TIME else DISPLAY_TIME

            if (!fadingOut && messageTimer > limit) {
                // Start fading out
                fadingOut = true
                messageTimer = 0f
            }

            if (fadingOut && messageTimer > FADE_DURATION) {
                // Fade finished
                if (messageQueue.isNotEmpty()) {
                    // Show next message
                    showNextMessage()
                } else {
                    // Clear completely
                    currentMessage = null
                    toDraw = null
                }
            }
        }
    }

    private fun showNextMessage() {
        currentMessage = messageQueue.removeFirstOrNull()
        messageTimer = 0f
        fadingOut = false

        toDraw = font?.createText(currentMessage?.first ?: "", currentMessage?.second ?: Color.WHITE, 24f)?.apply {
            maxWidth = 800f
        }
    }

    private fun render() {
        if (toDraw != null && currentMessage != null) {
            val screenWidth = Global.getSettings().screenWidth
            val x = (screenWidth - toDraw!!.width) / 2f
            val y = Global.getSettings().screenHeight - (TOP_BUFFER + if (curState == GameState.CAMPAIGN) 10f else 0f)

            val alpha = when {
                fadingOut -> {
                    val fadeProgress = messageTimer / FADE_DURATION
                    (255 * (1f - fadeProgress.coerceIn(0f, 1f))).toInt()
                }

                else -> 255
            }

            toDraw!!.baseColor = currentMessage!!.second.setAlpha(alpha)
            toDraw!!.draw(x, y)
        }
    }

    override fun renderInUICoords(viewport: ViewportAPI?) {
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun init(engine: CombatEngineAPI?) {
    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {}

    override fun processInputPreCoreControls(amount: Float, events: MutableList<InputEventAPI>?) {}
}