package com.dock.scanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustmentsTest {

    @Test
    fun zeroIsIdentity() {
        for (v in listOf(0, 37, 128, 200, 255)) assertEquals(v, Adjustments.apply(v, 0f, 0f))
    }

    @Test
    fun contrastPivotsAroundMidGray() {
        assertEquals(128, Adjustments.apply(128, 0f, 1f))
        assertEquals(255, Adjustments.apply(200, 0f, 1f))
        assertEquals(0, Adjustments.apply(60, 0f, 1f))
    }

    @Test
    fun matrixMatchesPerChannelMath() {
        val m = Adjustments.matrix(0.3f, -0.5f)
        val expected = Adjustments.apply(90, 0.3f, -0.5f)
        assertEquals(expected, (m[0] * 90 + m[4]).toInt().coerceIn(0, 255))
    }
}
