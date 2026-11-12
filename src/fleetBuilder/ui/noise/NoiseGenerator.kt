package fleetBuilder.ui.noise

import kotlin.random.Random

class NoiseGenerator {

    // --- Core state ---
    private var angle: Float = 0f
    private var amplitude: Float = 1f
    private var elapsed: Float = 0f
    private var duration: Float = 0f
    private var intensity: Float = 1f
    private var baseDuration: Float = 0.25f
    private var fadeStart: Float = 0f
    private var fadeDuration: Float = 0f
    private var cooldown: Float = 0f

    init {
        reset()
    }

    /**
     * Triggers a short burst (used externally sometimes)
     */
    fun trigger(shorter: Boolean) {
        cooldown = 0.01f

        duration = baseDuration * 0.4f + Random.nextFloat() * baseDuration * 0.6f
        if (shorter) {
            duration = baseDuration * 0.4f + Random.nextFloat() * baseDuration * 0.3f
        }

        intensity = 1f
        fadeDuration = duration / 5f
    }
    /*fun trigger(shorter: Boolean) {
        cooldown = 0.1f

        duration = if (shorter) {
            baseDuration * (0.3f + Random.nextFloat() * 0.3f)
        } else {
            baseDuration * (0.5f + Random.nextFloat() * 0.5f)
        }

        intensity = 0.7f + Random.nextFloat() * 0.3f
        fadeDuration = duration * 0.3f
        fadeStart = duration - fadeDuration
    }*/

    /**
     * Internal reset to a new random state
     */
    private fun reset() {
        duration = baseDuration * 0.25f + Random.nextFloat() * baseDuration * 0.75f

        intensity = Random.nextFloat() * 0.75f + 0.5f
        if (intensity > 1f) intensity = 1f

        fadeStart = 0f
        fadeDuration = duration / 5f
        elapsed = 0f

        angle = Random.nextFloat() * 360f
        amplitude = 1f
    }
    /*private fun reset() {
        duration = baseDuration * (0.5f + Random.nextFloat() * 0.75f)

        // 🔥 FIX: better distribution (no more always ~1)
        intensity = 0.3f + Random.nextFloat() * 0.7f

        fadeDuration = duration * (0.2f + Random.nextFloat() * 0.3f)
        fadeStart = duration - fadeDuration

        elapsed = 0f
        amplitude = 1f
        angle = Random.nextFloat() * 360f
    }*/

    /**
     * Current output value (what your renderer uses)
     */
    fun getValue(): Float {
        return amplitude * intensity
    }

    fun getAngle(): Float = angle

    /**
     * Advances the noise over time
     * @return true if a reset occurred this frame
     */
    fun advance(delta: Float): Boolean {
        elapsed += delta

        if (elapsed > duration && cooldown <= 0f) {
            reset()
            return true
        }

        if (elapsed > fadeStart + fadeDuration) {
            amplitude -= delta / (duration - fadeStart - fadeDuration)
            if (amplitude < 0f) amplitude = 0f
        } else {
            amplitude = 1f
        }

        return false
    }
    /*fun advance(delta: Float): Boolean {
        elapsed += delta

        if (cooldown > 0f) {
            cooldown -= delta
        }

        if (elapsed > duration && cooldown <= 0f) {
            reset()
            return true
        }

        // 🔥 smoother fade
        if (elapsed > fadeStart) {
            val t = (elapsed - fadeStart) / max(fadeDuration, 0.0001f)
            amplitude = (1f - t).coerceIn(0f, 1f)
        } else {
            amplitude = 1f
        }

        return false
    }*/

    // --- Getters / setters ---

    fun getDuration(): Float = duration
    fun setDuration(value: Float) {
        duration = value
    }

    fun getIntensity(): Float = intensity
    fun setIntensity(value: Float) {
        intensity = value
    }

    fun getBaseDuration(): Float = baseDuration
    fun setBaseDuration(value: Float) {
        baseDuration = value
    }

    fun getFadeStart(): Float = fadeStart
    fun setFadeStart(value: Float) {
        fadeStart = value
    }

    fun getFadeDuration(): Float = fadeDuration
    fun setFadeDuration(value: Float) {
        fadeDuration = value
    }
}