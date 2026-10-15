package com.dock.scanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetLayoutTest {

    /** Height of a placed page as a fraction of the sheet's height. */
    private fun SheetLayout.heightOf(p: Placement, imageAspect: Float) = p.width * aspect / imageAspect

    @Test
    fun clampAllKeepsARotatedPageOnTheSheet() {
        // A landscape photo (1.5:1) laid out on landscape A4, then rotated to portrait.
        val layout = SheetLayout(Paper.A4, landscape = true, items = listOf(Placement("a", 0.5f, 0.5f, 0.92f)))
        val rotated = 1 / 1.5f
        assertTrue("precondition: unclamped it overflows", layout.heightOf(layout.items.single(), rotated) > 1f)

        val fitted = layout.clampAll { rotated }
        val p = fitted.items.single()
        val h = fitted.heightOf(p, rotated)
        assertTrue(h <= 1f + 1e-4f)
        assertTrue(p.centerY - h / 2 >= -1e-4f && p.centerY + h / 2 <= 1f + 1e-4f)
        assertTrue(p.centerX - p.width / 2 >= -1e-4f && p.centerX + p.width / 2 <= 1f + 1e-4f)
    }

    @Test
    fun clampAllLeavesPagesThatAlreadyFitOrHaveNoShapeYet() {
        val layout = SheetLayout(
            Paper.A4, landscape = false,
            items = listOf(Placement("a", 0.5f, 0.3f, 0.4f), Placement("b", 0.5f, 0.7f, 0.9f)),
        )
        val fitted = layout.clampAll { id -> if (id == "a") 1 / 1.414f else null }
        assertEquals(layout, fitted)
    }
}
