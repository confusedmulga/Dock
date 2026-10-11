package com.dock.scanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadTest {

    @Test
    fun ordersShuffledCornersClockwiseFromTopLeft() {
        val tl = Point2(0.1f, 0.2f)
        val tr = Point2(0.9f, 0.15f)
        val br = Point2(0.85f, 0.9f)
        val bl = Point2(0.12f, 0.8f)
        for (perm in listOf(listOf(br, tl, bl, tr), listOf(tr, bl, tl, br), listOf(bl, br, tr, tl))) {
            assertEquals(Quad(tl, tr, br, bl), Quad.fromUnordered(perm))
        }
    }

    @Test
    fun ordersDiamondWithoutDuplicates() {
        // A page rotated ~45 degrees: sum/difference heuristics break here.
        val top = Point2(0.5f, 0.1f)
        val right = Point2(0.9f, 0.5f)
        val bottom = Point2(0.5f, 0.9f)
        val left = Point2(0.1f, 0.5f)
        val q = Quad.fromUnordered(listOf(bottom, left, right, top))
        assertEquals(4, q.corners.toSet().size)
        assertTrue(q.isConvex())
    }

    @Test
    fun detectsSelfIntersectingQuad() {
        val bowTie = Quad(Point2(0f, 0f), Point2(1f, 1f), Point2(1f, 0f), Point2(0f, 1f))
        assertFalse(bowTie.isConvex())
        assertTrue(Quad.FULL.isConvex())
    }

    @Test
    fun warpedSizeUsesLongestEdges() {
        val q = Quad(Point2(0f, 0f), Point2(0.5f, 0f), Point2(0.5f, 1f), Point2(0f, 1f))
        assertEquals(500 to 2000, q.warpedSize(1000, 2000))
    }

    @Test
    fun smootherHidesOutlineOnlyAfterSeveralMisses() {
        val smoother = QuadSmoother(maxMisses = 2)
        smoother.update(Quad.FULL)
        assertEquals(Quad.FULL, smoother.update(null))
        assertEquals(Quad.FULL, smoother.update(null))
        assertEquals(null, smoother.update(null))
    }
}
