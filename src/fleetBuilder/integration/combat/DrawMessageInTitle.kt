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
import org.magiclib.kotlin.setAlpha
import java.awt.Color

class DrawMessageInTitle : EveryFrameCombatPlugin {

    companion object {
        private val messageQueue = ArrayDeque<String>()
        private var currentMessage: String? = null
        private var messageTimer = 0f
        private var font: LazyFont? = null
        private var toDraw: LazyFont.DrawableString? = null

        /** Call this to queue a message */
        fun addMessage(text: String) {
            messageQueue.addLast(text)
        }
    }

    private var init = false

    @Deprecated("Deprecated in Java")
    override fun init(engine: CombatEngineAPI?) {
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
    }

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        val state = AppDriver.getInstance().currentState
        if (state !is TitleScreenState) return

        if (!init) {
            doInit()
            init = true
        }

        // If no current message, grab the next one
        if (currentMessage == null && messageQueue.isNotEmpty()) {
            showNextMessage()
        }

        // Update timer
        if (currentMessage != null) {
            messageTimer += amount

            if (messageQueue.isNotEmpty()) {
                // If another message is waiting, quickly replace after 0.5s
                if (messageTimer > 0.5f) {
                    showNextMessage()
                }
            } else {
                // No other message waiting, remove after 5s
                if (messageTimer > 5f) {
                    currentMessage = null
                    toDraw = null
                }
            }
        }
    }

    private fun showNextMessage() {
        currentMessage = messageQueue.removeFirstOrNull()
        messageTimer = 0f

        toDraw = font?.createText(currentMessage ?: "", Color.WHITE, 24f)?.apply {
            maxWidth = 600f
        }
    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {}

    override fun renderInUICoords(viewport: ViewportAPI?) {
        if (toDraw != null && currentMessage != null) {
            val screenWidth = Global.getSettings().screenWidth
            val x = (screenWidth - toDraw!!.width) / 2f
            val y = Global.getSettings().screenHeight - 10f // near top of screen
            toDraw!!.baseColor = Color.WHITE.setAlpha(55)
            toDraw!!.draw(x, y)
        }
    }

    override fun processInputPreCoreControls(amount: Float, events: MutableList<InputEventAPI>?) {}
}