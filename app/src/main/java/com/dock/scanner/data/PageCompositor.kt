package com.dock.scanner.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import com.dock.scanner.core.Adjustments
import java.io.File
import java.io.IOException

/**
 * Second, cheap render stage: brightness/contrast and ink strokes on top of the base image.
 * Uses the same color matrix and stroke geometry as the editor preview, so what you see
 * while editing is what lands in the PDF.
 */
object PageCompositor {

    fun needsCompositing(page: Page) =
        page.strokes.isNotEmpty() || !Adjustments.isIdentity(page.brightness, page.contrast)

    fun compose(base: File, brightness: Float, contrast: Float, strokes: List<InkStroke>, output: File, jpegQuality: Int = 88) {
        val src = BitmapFactory.decodeFile(base.absolutePath) ?: throw IOException("Can't decode ${base.name}")
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val adjust = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (!Adjustments.isIdentity(brightness, contrast)) {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(Adjustments.matrix(brightness, contrast)))
            }
        }
        canvas.drawBitmap(src, 0f, 0f, adjust)
        src.recycle()
        strokes.forEach { draw(canvas, it, result.width.toFloat(), result.height.toFloat()) }

        val tmp = File(output.parentFile, "tmp_" + output.name)
        try {
            tmp.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, jpegQuality, it) }
            if (!tmp.renameTo(output)) throw IOException("Could not write ${output.name}")
        } finally {
            result.recycle()
            tmp.delete()
        }
    }

    private fun draw(canvas: Canvas, stroke: InkStroke, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.width * width
            color = stroke.color
        }
        canvas.drawPath(strokePath(stroke, width, height), paint)
    }

    /** Smooths the polyline with quadratic curves through the midpoints of its segments. */
    fun strokePath(stroke: InkStroke, width: Float, height: Float): Path {
        val pts = stroke.points
        val path = Path()
        if (pts.isEmpty()) return path
        path.moveTo(pts[0].x * width, pts[0].y * height)
        if (pts.size == 1) {
            path.lineTo(pts[0].x * width + 0.01f, pts[0].y * height) // a dot
            return path
        }
        for (i in 1 until pts.size - 1) {
            val mx = (pts[i].x + pts[i + 1].x) / 2 * width
            val my = (pts[i].y + pts[i + 1].y) / 2 * height
            path.quadTo(pts[i].x * width, pts[i].y * height, mx, my)
        }
        path.lineTo(pts.last().x * width, pts.last().y * height)
        return path
    }
}
