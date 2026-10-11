package com.dock.scanner.core

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * Finds the outline of a sheet of paper in a grayscale image using classic computer
 * vision (no ML): text is erased with a morphological close, edges come from Canny and
 * the largest convex four-sided contour wins. An Otsu-threshold pass is the fallback for
 * low-contrast scenes where Canny leaves gaps.
 *
 * Not thread-safe; give each worker thread its own instance.
 */
class DocumentDetector(
    private val workSize: Int = 480,
    private val minAreaFraction: Double = 0.08,
) {
    private val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
    private val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
    private val supportKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

    /**
     * @param gray single-channel 8-bit image, any size. Not modified.
     * @param rotationDegrees clockwise rotation that makes [gray] upright (0/90/180/270).
     * @return corners normalized to the upright image, or null when no document was found.
     */
    fun detect(gray: Mat, rotationDegrees: Int = 0): Quad? {
        val small = Mat()
        val work = Mat()
        try {
            val scale = workSize.toDouble() / max(gray.cols(), gray.rows())
            if (scale < 1.0) {
                Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
            } else {
                gray.copyTo(small)
            }
            rotate(small, work, rotationDegrees)

            Imgproc.morphologyEx(work, work, Imgproc.MORPH_CLOSE, closeKernel)
            Imgproc.GaussianBlur(work, work, Size(5.0, 5.0), 0.0)

            val quad = findWithCanny(work) ?: findWithThreshold(work) ?: return null
            val w = work.cols().toFloat()
            val h = work.rows().toFloat()
            return Quad.fromUnordered(quad.map { Point2((it.x / w).toFloat(), (it.y / h).toFloat()).clamped() })
        } finally {
            small.release()
            work.release()
        }
    }

    private fun findWithCanny(blurred: Mat): List<Point>? {
        val edges = Mat()
        val nearEdges = Mat()
        try {
            val median = median(blurred)
            val lower = (0.66 * median).coerceIn(10.0, 120.0)
            val upper = (1.33 * median).coerceIn(lower + 30.0, 255.0)
            Imgproc.Canny(blurred, edges, lower, upper)
            Imgproc.dilate(edges, edges, dilateKernel)
            Imgproc.dilate(edges, nearEdges, supportKernel)
            return largestQuad(edges, blurred.cols() * blurred.rows(), nearEdges)
        } finally {
            edges.release()
            nearEdges.release()
        }
    }

    private fun findWithThreshold(blurred: Mat): List<Point>? {
        val binary = Mat()
        try {
            Imgproc.threshold(blurred, binary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            return largestQuad(binary, blurred.cols() * blurred.rows(), nearEdges = null)
        } finally {
            binary.release()
        }
    }

    /**
     * Picks the biggest convex 4-sided shape among the contours. Candidates are judged by
     * their convex hull, so an outline with gaps (a corner lost in shadow, a finger over an
     * edge) still counts as long as most of it is visible.
     */
    private fun largestQuad(binary: Mat, imageArea: Int, nearEdges: Mat?): List<Point>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        val minArea = imageArea * minAreaFraction
        // A shape covering the whole frame is the image border itself (e.g. an all-white
        // threshold), not a document.
        val maxArea = imageArea * 0.97
        val candidates = contours.mapNotNull { contour ->
            val points = contour.toArray()
            val hull = convexHull(contour, points)
            val area = Imgproc.contourArea(hull)
            if (area in minArea..maxArea) Candidate(points, hull, area) else {
                hull.release()
                null
            }
        }.sortedByDescending { it.hullArea }
        contours.forEach { it.release() }

        var best: List<Point>? = null
        var bestArea = 0.0
        for (candidate in candidates.take(MAX_CANDIDATES)) {
            val quad = approximateQuad(candidate, minArea, nearEdges) ?: continue
            val area = polygonArea(quad)
            if (area > bestArea) {
                best = quad
                bestArea = area
            }
        }
        candidates.forEach { it.hull.release() }
        return best
    }

    private class Candidate(val points: Array<Point>, val hull: MatOfPoint2f, val hullArea: Double)

    private fun convexHull(contour: MatOfPoint, points: Array<Point>): MatOfPoint2f {
        val indices = MatOfInt()
        Imgproc.convexHull(contour, indices)
        val hull = MatOfPoint2f(*indices.toArray().map { points[it] }.toTypedArray())
        indices.release()
        return hull
    }

    /**
     * @param nearEdges slightly widened edge map; when given, most of the quad's border must
     *   run along real edges, which rejects hulls wrapped around unrelated clutter.
     */
    private fun approximateQuad(candidate: Candidate, minArea: Double, nearEdges: Mat?): List<Point>? {
        for (epsilon in doubleArrayOf(0.02, 0.03, 0.045, 0.06)) {
            val quad = polygon(candidate.hull, epsilon) ?: continue
            val area = polygonArea(quad)
            // The quad must describe the hull, not cut big corners off it.
            if (area < minArea || kotlin.math.abs(area - candidate.hullArea) / candidate.hullArea >= 0.1) return null
            if (nearEdges != null && edgeSupport(quad, nearEdges) < MIN_EDGE_SUPPORT) return null
            return quad
        }
        return null
    }

    /** Fraction of points sampled along the quad's border that lie on an edge. */
    private fun edgeSupport(quad: List<Point>, nearEdges: Mat): Double {
        val samplesPerSide = 50
        val pixel = ByteArray(1)
        var total = 0
        var hits = 0
        for (i in quad.indices) {
            val a = quad[i]
            val b = quad[(i + 1) % quad.size]
            for (s in 0 until samplesPerSide) {
                val t = s.toDouble() / samplesPerSide
                val x = (a.x + (b.x - a.x) * t).toInt()
                val y = (a.y + (b.y - a.y) * t).toInt()
                if (x < 0 || y < 0 || x >= nearEdges.cols() || y >= nearEdges.rows()) continue
                total++
                nearEdges.get(y, x, pixel)
                if (pixel[0].toInt() != 0) hits++
            }
        }
        return if (total == 0) 0.0 else hits.toDouble() / total
    }

    private fun polygon(curve: MatOfPoint2f, epsilonFraction: Double): List<Point>? {
        val approx = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(curve, true)
            Imgproc.approxPolyDP(curve, approx, epsilonFraction * perimeter, true)
            if (approx.rows() != 4) return null
            val points = approx.toArray()
            val asInt = MatOfPoint(*points)
            val convex = Imgproc.isContourConvex(asInt)
            asInt.release()
            return if (convex) points.toList() else null
        } finally {
            approx.release()
        }
    }

    private fun polygonArea(points: List<Point>): Double {
        var twice = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twice += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(twice) / 2
    }

    private fun median(gray: Mat): Double {
        val hist = IntArray(256)
        val bytes = ByteArray(gray.cols() * gray.rows())
        gray.get(0, 0, bytes)
        for (b in bytes) hist[b.toInt() and 0xFF]++
        val half = bytes.size / 2
        var acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= half) return i.toDouble()
        }
        return 127.0
    }

    companion object {
        private const val MAX_CANDIDATES = 8
        private const val MIN_EDGE_SUPPORT = 0.6

        fun rotate(src: Mat, dst: Mat, degrees: Int) {
            when (((degrees % 360) + 360) % 360) {
                90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
                180 -> Core.rotate(src, dst, Core.ROTATE_180)
                270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> if (src !== dst) src.copyTo(dst)
            }
        }
    }
}
