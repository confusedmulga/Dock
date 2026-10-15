package com.dock.scanner.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRenderSizeTest {

    /** Android's limit for drawing one bitmap (RecordingCanvas). */
    private val maxDrawBytes = 100L * 1024 * 1024

    @Test
    fun normalPagesRenderAtTheRequestedWidth() {
        assertEquals(1741 to 2463, pageRenderSize(1741, 595, 842))
    }

    @Test
    fun veryTallPagesStayDrawableAndKeepTheirShape() {
        // A long receipt: 1:12, at the viewer's widest render.
        val (w, h) = pageRenderSize(2000, 595, 595 * 12)
        assertTrue("precondition: unscaled it's too big", 2000L * 2000 * 12 * 4 > maxDrawBytes)
        assertTrue(w.toLong() * h * 4 < maxDrawBytes)
        assertEquals(12.0, h.toDouble() / w, 0.05)
    }

    @Test
    fun extremeShapesNeverGiveAnEmptyBitmap() {
        val (w, h) = pageRenderSize(2000, 595, 1)
        assertTrue(w >= 1 && h >= 1)
    }
}
