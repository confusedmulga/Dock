package com.dock.scanner.core

import kotlin.math.pow

/**
 * Brightness and contrast as one 4x5 color matrix (the format both android.graphics and
 * Compose use), so the live preview and the final render apply exactly the same math.
 *
 * Both values run from -1 to 1, with 0 meaning unchanged. Contrast scales around mid-gray
 * (x3 at +1, x1/3 at -1); brightness shifts by up to ±100 levels.
 */
object Adjustments {

    fun isIdentity(brightness: Float, contrast: Float) = brightness == 0f && contrast == 0f

    fun matrix(brightness: Float, contrast: Float): FloatArray {
        val scale = 3f.pow(contrast)
        val offset = 128f * (1f - scale) + brightness * 100f
        return floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Applies the adjustment to one 0-255 channel value. */
    fun apply(value: Int, brightness: Float, contrast: Float): Int {
        val scale = 3f.pow(contrast)
        return (scale * value + 128f * (1f - scale) + brightness * 100f).toInt().coerceIn(0, 255)
    }
}
