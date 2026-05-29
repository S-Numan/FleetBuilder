package fleetBuilder.ui.customPanel.elements

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.otherMods.starficz.StarUIPanelPlugin
import fleetBuilder.ui.UIUtils
import org.lwjgl.opengl.GL11
import java.awt.Color

// TODO, fader for press in and out

internal class StarUIButton(
    var width: Float,
    var height: Float
) : StarUIPanelPlugin() {

    // === Rendering Config ===
    var renderBackground = true
    var renderSprite = true

    // === Visuals ===
    var backgroundColor: Color = Color(40, 40, 40, 150)
    var hoverColor: Color = Color(255, 255, 255, 80)

    var pressedColor: Color = Color(100, 255, 100, 120) // shown when pressed/toggled

    var sprite: SpriteAPI? = null
        private set

    // === Behavior Config ===
    var isToggle = false
    var toggled = false
        private set

    var triggerOnPress = true // true = mouse down, false = release

    // === Sound ===
    var soundId: String? = null
    var soundVolume = 1f
    var soundPitch = 1f

    private val hoverFader = Fader(0f, 0.1f, 0.1f, false, false)

    // === Public Callback ===
    private var onTriggerFunction: (() -> Unit)? = null
    fun onTrigger(callback: () -> Unit) {
        onTriggerFunction = callback
    }

    // === Setup ===
    fun setSprite(newSprite: SpriteAPI?) {
        sprite = newSprite
    }

    fun setBackground(enabled: Boolean) {
        renderBackground = enabled
    }

    fun setSpriteRendering(enabled: Boolean) {
        renderSprite = enabled
    }

    // === Init Hook ===
    fun init(panel: CustomPanelAPI) {
        this.panel = panel

        hoverFader.fadeOut()

        onClick {
            if (triggerOnPress) trigger()
        }

        onClickRelease {
            if (!triggerOnPress) trigger()
        }

        onHoverEnter {
            hoverFader.fadeIn()
        }

        onHoverExit {
            hoverFader.fadeOut()
        }

        advance { amount ->
            hoverFader.advance(amount)
        }
    }

    private fun trigger() {
        if (isToggle) {
            toggled = !toggled
        }

        // play sound
        soundId?.let {
            Global.getSoundPlayer().playUISound(it, soundPitch, soundVolume)
        }

        onTriggerFunction?.invoke()
    }

    // === Rendering ===
    init {
        val panel = Global.getSettings().createCustom(width, height, this)
        init(panel)

        renderBelow { alpha ->
            val x = panel.position.x
            val y = panel.position.y

            val hoverAlpha = hoverFader.brightness

            // === Background ===
            if (renderBackground) {
                val base = backgroundColor

                GL11.glColor4f(
                    base.red / 255f,
                    base.green / 255f,
                    base.blue / 255f,
                    (base.alpha / 255f) * alpha
                )

                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex2f(x, y)
                GL11.glVertex2f(x + width, y)
                GL11.glVertex2f(x + width, y + height)
                GL11.glVertex2f(x, y + height)
                GL11.glEnd()

                // Hover overlay (faded)
                if (hoverAlpha > 0f) {
                    GL11.glColor4f(
                        hoverColor.red / 255f,
                        hoverColor.green / 255f,
                        hoverColor.blue / 255f,
                        (hoverColor.alpha / 255f) * hoverAlpha * alpha
                    )

                    UIUtils.drawRectGL(x, y, width, height)
                }
            }

            // === Press / Toggle Overlay ===
            if (hasClicked || (isToggle && toggled)) {
                GL11.glColor4f(
                    pressedColor.red / 255f,
                    pressedColor.green / 255f,
                    pressedColor.blue / 255f,
                    (pressedColor.alpha / 255f) * alpha
                )

                UIUtils.drawRectGL(x, y, width, height)
            }
        }

        render { alpha ->
            val x = panel.position.x
            val y = panel.position.y

            // === Sprite ===
            if (renderSprite) {
                sprite?.let {
                    it.setAlphaMult(alpha)
                    it.setSize(width, height)
                    it.renderAtCenter(x + width / 2f, y + height / 2f)
                }
            }
        }
    }
}