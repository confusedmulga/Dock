package com.dock.scanner.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class DocumentDetectorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun init() {
            assertTrue(PageProcessor.initOpenCv())
        }
    }

    /** A light, tilted "page" with lines of "text" on a darker, noisy "table". */
    private fun syntheticPhoto(w: Int, h: Int, corners: List<Point>): Mat {
        val img = Mat(h, w, CvType.CV_8UC1, Scalar(70.0))
        val noise = Mat(h, w, CvType.CV_8UC1)
        org.opencv.core.Core.randn(noise, 0.0, 8.0)
        org.opencv.core.Core.add(img, noise, img)
        Imgproc.fillConvexPoly(img, MatOfPoint(*corners.toTypedArray()), Scalar(215.0))
        val (tl, tr, _, bl) = corners
        for (line in 1..12) {
            val t = line / 14.0
            val y0 = tl.y + (bl.y - tl.y) * t
            val x0 = tl.x + (bl.x - tl.x) * t + 30
            Imgproc.line(img, Point(x0, y0), Point(x0 + (tr.x - tl.x) * 0.7, y0 + (tr.y - tl.y) * 0.7), Scalar(40.0), 3)
        }
        return img
    }

    private fun assertMatches(expected: List<Point>, w: Int, h: Int, actual: Quad?) {
        assertNotNull("no document found", actual)
        val normalized = Quad.fromUnordered(expected.map { Point2((it.x / w).toFloat(), (it.y / h).toFloat()) })
        val error = normalized.maxCornerDistance(actual!!)
        assertTrue("corner error $error too large: $actual", error < 0.025f)
    }

    @Test
    fun findsTiltedPage() {
        val corners = listOf(Point(150.0, 90.0), Point(560.0, 130.0), Point(520.0, 600.0), Point(110.0, 560.0))
        val img = syntheticPhoto(640, 720, corners)
        assertMatches(corners, 640, 720, DocumentDetector().detect(img))
    }

    @Test
    fun appliesSensorRotation() {
        // Camera buffers arrive sideways; rotating the expected corners 90 degrees clockwise
        // must give the same answer as asking the detector to rotate.
        val corners = listOf(Point(150.0, 90.0), Point(560.0, 130.0), Point(520.0, 600.0), Point(110.0, 560.0))
        val img = syntheticPhoto(640, 720, corners)
        val sideways = Mat()
        DocumentDetector.rotate(img, sideways, 270)
        val quad = DocumentDetector().detect(sideways, rotationDegrees = 90)
        assertMatches(corners, 640, 720, quad)
    }

    @Test
    fun findsPageWhoseCornerIsInShadow() {
        // Realistic photo: perspective, wood texture, lighting falling off so the bottom-right
        // edge almost vanishes. Regression test: the outline has a gap there.
        val context = InstrumentationRegistry.getInstrumentation().context
        val file = File(context.cacheDir, "shadowed_invoice.jpg")
        context.assets.open("shadowed_invoice.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
        val quad = PageProcessor.detect(file, DocumentDetector())
        // Corners the generator placed, in a 3000x4000 frame.
        val expected = listOf(Point(620.0, 560.0), Point(2420.0, 760.0), Point(2560.0, 3420.0), Point(430.0, 3260.0))
        assertMatches(expected, 3000, 4000, quad)
    }

    @Test
    fun returnsNullForEmptyScene() {
        val img = Mat(480, 640, CvType.CV_8UC1, Scalar(90.0))
        assertNull(DocumentDetector().detect(img))
    }
}
