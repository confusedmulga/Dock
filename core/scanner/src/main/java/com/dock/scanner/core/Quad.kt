package com.dock.scanner.core

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/** A point in normalized image coordinates: (0,0) is top-left, (1,1) is bottom-right. */
data class Point2(val x: Float, val y: Float) {
    fun distanceTo(other: Point2): Float = hypot(x - other.x, y - other.y)

    fun clamped(): Point2 = Point2(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * The four corners of a document, in normalized coordinates of an upright image,
 * ordered clockwise starting at the top-left.
 */
data class Quad(val tl: Point2, val tr: Point2, val br: Point2, val bl: Point2) {

    val corners: List<Point2> get() = listOf(tl, tr, br, bl)

    fun withCorner(index: Int, point: Point2): Quad = when (index) {
        0 -> copy(tl = point)
        1 -> copy(tr = point)
        2 -> copy(br = point)
        3 -> copy(bl = point)
        else -> throw IndexOutOfBoundsException("Corner index $index")
    }

    fun lerp(target: Quad, t: Float): Quad {
        fun mix(a: Point2, b: Point2) = Point2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        return Quad(mix(tl, target.tl), mix(tr, target.tr), mix(br, target.br), mix(bl, target.bl))
    }

    fun maxCornerDistance(other: Quad): Float =
        corners.zip(other.corners).maxOf { (a, b) -> a.distanceTo(b) }

    /** True when every turn has the same direction, i.e. the quad can be warped sensibly. */
    fun isConvex(): Boolean {
        val pts = corners
        var sign = 0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) return false
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** Output size in pixels when this quad is cut out of an image of [width] x [height]. */
    fun warpedSize(width: Int, height: Int): Pair<Int, Int> {
        fun d(a: Point2, b: Point2) = hypot((a.x - b.x) * width, (a.y - b.y) * height)
        val w = max(d(tl, tr), d(bl, br))
        val h = max(d(tl, bl), d(tr, br))
        return w.toInt().coerceAtLeast(1) to h.toInt().coerceAtLeast(1)
    }

    companion object {
        val FULL = Quad(Point2(0f, 0f), Point2(1f, 0f), Point2(1f, 1f), Point2(0f, 1f))

        /**
         * Orders four arbitrary points clockwise from the top-left. Sorting by angle around
         * the centroid stays correct for strongly rotated documents, where the usual
         * "smallest x+y" heuristics produce duplicate corners.
         */
        fun fromUnordered(points: List<Point2>): Quad {
            require(points.size == 4) { "Expected 4 points, got ${points.size}" }
            val cx = points.sumOf { it.x.toDouble() } / 4
            val cy = points.sumOf { it.y.toDouble() } / 4
            // In image coordinates (y down) ascending atan2 walks clockwise on screen.
            val sorted = points.sortedBy { atan2(it.y - cy, it.x - cx) }
            val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            val o = List(4) { sorted[(start + it) % 4] }
            return Quad(o[0], o[1], o[2], o[3])
        }
    }
}
