package com.dock.scanner.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.dock.scanner.core.Quad

/** Maps a normalized quad onto [area] (the on-screen rectangle the image occupies). */
fun Quad.toOffsets(area: Rect): List<Offset> =
    corners.map { Offset(area.left + it.x * area.width, area.top + it.y * area.height) }

fun DrawScope.drawQuad(
    quad: Quad,
    area: Rect,
    color: Color,
    strokeWidth: Float,
    fillAlpha: Float = 0.18f,
    handleRadius: Float = 0f,
    activeHandle: Int = -1,
) {
    val points = quad.toOffsets(area)
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1..3) lineTo(points[i].x, points[i].y)
        close()
    }
    drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = Stroke(width = strokeWidth))
    if (handleRadius > 0f) {
        points.forEachIndexed { i, p ->
            val r = if (i == activeHandle) handleRadius * 1.3f else handleRadius
            drawCircle(Color.White, r, p)
            drawCircle(color, r, p, style = Stroke(width = strokeWidth))
        }
    }
}
