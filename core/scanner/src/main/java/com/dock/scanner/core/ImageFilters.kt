package com.dock.scanner.core

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/** Document enhancement filters. Every function takes a BGR [Mat] and returns a new one. */
object ImageFilters {

    /** Long side of the low-resolution copy used to estimate the paper background. */
    private const val BACKGROUND_SIZE = 512.0

    fun apply(bgr: Mat, filter: PageFilter): Mat = when (filter) {
        PageFilter.ORIGINAL -> bgr.clone()
        PageFilter.AUTO -> autoColor(bgr)
        PageFilter.GRAYSCALE -> grayscale(bgr)
        PageFilter.BLACK_WHITE -> blackAndWhite(bgr)
    }

    /** Per-channel shadow removal: also white-balances the paper. */
    private fun autoColor(bgr: Mat): Mat {
        val channels = ArrayList<Mat>(3)
        Core.split(bgr, channels)
        val flattened = channels.map { channel ->
            flattenIllumination(channel).also { boostContrast(it); channel.release() }
        }
        val out = Mat()
        Core.merge(flattened, out)
        flattened.forEach { it.release() }
        return out
    }

    private fun grayscale(bgr: Mat): Mat {
        val gray = toGray(bgr)
        val out = flattenIllumination(gray)
        gray.release()
        boostContrast(out)
        return out
    }

    private fun blackAndWhite(bgr: Mat): Mat {
        val gray = toGray(bgr)
        val flat = flattenIllumination(gray)
        gray.release()
        Imgproc.GaussianBlur(flat, flat, Size(3.0, 3.0), 0.0)

        // Adaptive threshold keeps faint strokes; the global one keeps the inside of
        // large dark areas solid instead of hollow. A pixel is ink if either says so.
        var block = (max(flat.cols(), flat.rows()) / 50).coerceIn(15, 61)
        if (block % 2 == 0) block++
        val adaptive = Mat()
        Imgproc.adaptiveThreshold(
            flat, adaptive, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, block, 12.0,
        )
        val global = Mat()
        Imgproc.threshold(flat, global, 150.0, 255.0, Imgproc.THRESH_BINARY)
        val out = Mat()
        Core.bitwise_and(adaptive, global, out)
        flat.release()
        adaptive.release()
        global.release()
        return out
    }

    private fun toGray(bgr: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    /**
     * Estimates the paper (text removed via dilate + median on a small copy) and divides
     * it out, so shadows and uneven lighting become uniform white.
     */
    private fun flattenIllumination(channel: Mat): Mat {
        val scale = (BACKGROUND_SIZE / max(channel.cols(), channel.rows())).coerceAtMost(1.0)
        val small = Mat()
        Imgproc.resize(channel, small, Size(), scale, scale, Imgproc.INTER_AREA)
        Imgproc.dilate(small, small, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0)))
        Imgproc.medianBlur(small, small, 21)
        val background = Mat()
        Imgproc.resize(small, background, channel.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        small.release()

        val out = Mat()
        Core.divide(channel, background, out, 255.0)
        background.release()
        return out
    }

    /** Darkens mid-tones (ink) while keeping white paper at 255. */
    private fun boostContrast(mat: Mat) {
        mat.convertTo(mat, -1, 1.2, -51.0)
    }
}
