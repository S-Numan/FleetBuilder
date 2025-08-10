package fleetBuilder.integration.combat

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.title.TitleScreenState
import com.fs.state.AppDriver
import org.lazywizard.lazylib.ui.FontException
import org.lazywizard.lazylib.ui.LazyFont
import java.awt.Color

class DrawMessageInTitle : EveryFrameCombatPlugin {

    companion object {
        private val messageQueue = ArrayDeque<String>()
        private var currentMessage: String? = null
        private var messageTimer = 0f
        private var font: LazyFont? = null
        private var toDraw: LazyFont.DrawableString? = null

        // Config
        private const val DISPLAY_TIME = 5f         // seconds before fade starts (no other message)
        private const val FADE_DURATION = 0.5f        // fade-out time in seconds
        private const val QUICK_DISPLAY_TIME = 0.25f // time before fade starts if another message is waiting

        /** Call this to queue a message */
        fun addMessage(text: String) {
            messageQueue.addLast(text)
        }
    }

    private var init = false
    private var fadingOut = false

    override fun init(engine: CombatEngineAPI?) {}

    private fun doInit() {
        if (font == null) {
            try {
                font = LazyFont.loadFont("graphics/fonts/orbitron24aabold.fnt")
            } catch (e: FontException) {
                Global.getLogger(this.javaClass).error("Failed to load font", e)
                return
            }
        }
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        val state = AppDriver.getInstance().currentState
        if (state !is TitleScreenState) return

        if (!init) {
            doInit()
            init = true
        }

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

        toDraw = font?.createText(currentMessage ?: "", Color.WHITE, 24f)?.apply {
            maxWidth = 600f
        }
    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {}

    override fun renderInUICoords(viewport: ViewportAPI?) {
        if (toDraw != null && currentMessage != null) {
            val screenWidth = Global.getSettings().screenWidth
            val x = (screenWidth - toDraw!!.width) / 2f
            val y = Global.getSettings().screenHeight - 10f

            val alpha = when {
                fadingOut -> {
                    val fadeProgress = messageTimer / FADE_DURATION
                    (255 * (1f - fadeProgress.coerceIn(0f, 1f))).toInt()
                }

                else -> 255
            }

            toDraw!!.baseColor = Color(255, 255, 255, alpha)
            toDraw!!.draw(x, y)
        }
    }

    override fun processInputPreCoreControls(amount: Float, events: MutableList<InputEventAPI>?) {}
}
