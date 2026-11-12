package fleetBuilder.ui.noise

import com.fs.graphics.Sprite
import com.fs.graphics.util.Fader
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.util.Misc
import fleetBuilder.otherMods.starficz.ReflectionUtils.getFieldsMatching
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*

class UINoiseRenderer() {
    val sprite: Sprite
    private var color: Color = Color(255, 255, 255, 255)
    private val noise = NoiseGenerator()
    private val fader = Fader(0f, 1f, 1f)
    private var seed: Long = 0L


    init {
        val noiseSpriteAPI = Global.getSettings().getSprite("ui", "noise")
        sprite = noiseSpriteAPI.getFieldsMatching(type = Sprite::class.java).getOrNull(0)?.get(noiseSpriteAPI) as Sprite

        fader.isBounceDown = true;
        fader.forceOut();
        color = Global.getSettings().getColor("noiseColor")
        //noise.setBaseDuration()
    }

    fun getFader(): Fader = fader

    val constantIntensity = 0.35f
    val variableIntensity = 0.4f

    val defaultInDuration = 0.05f
    val defaultOutDuration = 0.8f

    fun getIntensity(): Float {
        val base = constantIntensity + variableIntensity * noise.getValue()
        return base * fader.brightness
    }

    fun getBrightness(): Float = fader.brightness

    /*fun fadeIn(duration: Float = defaultInDuration) {
        fader.durationIn = duration
        fader.fadeIn()
    }

    fun fadeOut(duration: Float = defaultOutDuration) {
        fader.durationOut = duration
        fader.fadeOut()
    }*/

    fun fadeInOut(
        inDuration: Float = defaultInDuration,
        outDuration: Float = defaultOutDuration,
        burst: Boolean = false
    ) {
        fader.setDuration(inDuration, outDuration)
        fader.fadeIn()
        if (burst)
            noise.trigger(false)
    }


    fun isFullyVisible(): Boolean {
        return fader.brightness == 1f
    }

    fun setColor(color: Color) {
        this.color = color
    }

    fun advance(amount: Float) {
        if (fader.brightness > 0f) {
            noise.advance(amount)
        }

        fader.advance(amount)
        seed = Misc.random.nextLong()
    }

    fun render(x: Float, y: Float, width: Float, height: Float, alphaMult: Float) {
        val enabled = true

        if (!enabled) return

        val intensity = getIntensity() * alphaMult
        if (intensity <= 0f) return

        val random = if (seed != 0L) Random(seed) else Random()

        renderNoise(
            sprite,
            intensity,
            x,
            y,
            width,
            height,
            random.nextFloat(),
            random.nextFloat(),
            width / sprite.imageWidth,
            height / sprite.imageHeight
        )
    }

    private fun renderNoise(
        sprite: Sprite,
        alpha: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u: Float,
        v: Float,
        uScale: Float,
        vScale: Float
    ) {
        GL11.glPushMatrix()
        GL11.glTranslatef(x, y, 0f)

        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)

        // Apply color + alpha
        sprite.color = Color(
            color.red,
            color.green,
            color.blue,
            (color.alpha * alpha).toInt().coerceIn(0, 255)
        )

        // --- UV scrolling (THIS replaces your manual texcoord math) ---
        sprite.texX = u
        sprite.texY = v
        sprite.texWidth = uScale
        sprite.texHeight = vScale

        // --- Size ---
        sprite.setSize(width, height)

        // --- Draw ---
        sprite.render(0f, 0f)

        GL11.glDisable(GL11.GL_BLEND)
        GL11.glPopMatrix()
    }
}