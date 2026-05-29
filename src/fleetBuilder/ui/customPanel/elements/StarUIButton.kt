package fleetBuilder.ui.customPanel.elements

import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.graphics.SpriteAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import fleetBuilder.otherMods.starficz.StarUIPanelPlugin
import fleetBuilder.ui.UIUtils
import org.lwjgl.opengl.GL11
import java.awt.Color

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
    var pressedColor: Color = Color(100, 255, 100, 120)

    // === Sprites ===
    private var defaultSprite: SpriteAPI? = null
    private var hoverSprite: SpriteAPI? = null
    private var pressedSprite: SpriteAPI? = null
    private var toggledSprite: SpriteAPI? = null

    // === Behavior Config ===
    var isToggle = false
    var toggled = false
        private set

    var triggerOnPress = true

    var isDisabled = false

    // === Sound ===
    var soundId: String? = null
    var soundVolume = 1f
    var soundPitch = 1f

    // === Faders ===
    private val hoverFader = Fader(0f, 0.1f, 0.1f, false, false)
    private val pressFader = Fader(0f, 0.08f, 0.08f, false, false)

    // === Internal ===
    private var isPressed = false

    // === Callback ===
    private var onTriggerFunction: (() -> Unit)? = null
    fun onTrigger(callback: () -> Unit) {
        onTriggerFunction = callback
    }

    // === Sprite Setup ===
    fun setSprite(
        default: SpriteAPI?,
        hover: SpriteAPI? = null,
        pressed: SpriteAPI? = null,
        toggled: SpriteAPI? = null
    ) {
        defaultSprite = default
        hoverSprite = hover
        pressedSprite = pressed
        toggledSprite = toggled
    }

    private fun getCurrentSprite(): SpriteAPI? {
        return when {
            isDisabled -> defaultSprite
            (isPressed) && pressedSprite != null -> pressedSprite
            (isToggle && toggled) && toggledSprite != null -> toggledSprite
            isHovering && hoverSprite != null -> hoverSprite
            else -> defaultSprite
        }
    }

    fun setBackground(enabled: Boolean) {
        renderBackground = enabled
    }

    fun setSpriteRendering(enabled: Boolean) {
        renderSprite = enabled
    }

    // === Init ===
    fun init(panel: CustomPanelAPI) {
        this.panel = panel

        allowedMouseButtons.add(0)

        hoverFader.fadeOut()
        pressFader.fadeOut()

        onClick {
            if (isDisabled) return@onClick

            isPressed = true
            pressFader.fadeIn()

            if (triggerOnPress) trigger()
        }

        onClickRelease {
            if (isDisabled) return@onClickRelease

            if (!triggerOnPress && isPressed) trigger()

            isPressed = false
            pressFader.fadeOut()
        }

        onClickReleaseOutside {
            if (isDisabled) return@onClickReleaseOutside

            isPressed = false
            pressFader.fadeOut()
        }

        onHoverEnter {
            if (isDisabled) return@onHoverEnter
            hoverFader.fadeIn()
        }

        onHoverExit {
            hoverFader.fadeOut()
        }

        advance { amount ->
            hoverFader.advance(amount)
            pressFader.advance(amount)
        }
    }

    private fun trigger() {
        if (isToggle) {
            toggled = !toggled
        }

        soundId?.let {
            UIUtils.playSound(it, soundVolume, soundPitch)
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
            val pressAlpha = pressFader.brightness

            // === Background ===
            if (renderBackground) {
                GL11.glColor4f(
                    backgroundColor.red / 255f,
                    backgroundColor.green / 255f,
                    backgroundColor.blue / 255f,
                    (backgroundColor.alpha / 255f) * alpha
                )

                UIUtils.drawRectGL(x, y, width, height)

                // Hover overlay
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

            // === Press / Toggle Overlay (with fade) ===
            val showPress = pressAlpha > 0f || (isToggle && toggled)
            if (false) {//showPress) {
                val effectiveAlpha = if (isToggle && toggled) 1f else pressAlpha

                GL11.glColor4f(
                    pressedColor.red / 255f,
                    pressedColor.green / 255f,
                    pressedColor.blue / 255f,
                    (pressedColor.alpha / 255f) * effectiveAlpha * alpha
                )

                UIUtils.drawRectGL(x, y, width, height)
            }

            // === Disabled Overlay ===
            if (isDisabled) {
                GL11.glColor4f(0f, 0f, 0f, 0.5f * alpha)
                UIUtils.drawRectGL(x, y, width, height)
            }
        }

        render { alpha ->
            val x = panel.position.x
            val y = panel.position.y

            if (!renderSprite) return@render

            val sprite = getCurrentSprite() ?: return@render

            val finalAlpha = if (isDisabled) alpha * 0.4f else alpha

            sprite.setAlphaMult(finalAlpha)
            sprite.setSize(width, height)
            sprite.renderAtCenter(x + width / 2f, y + height / 2f)
        }
    }
}