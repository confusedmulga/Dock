package com.dock.scanner.ui.editor

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.magnifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dock.scanner.core.Point2
import com.dock.scanner.core.Quad
import com.dock.scanner.ui.components.drawQuad
import com.dock.scanner.ui.components.toOffsets
import java.io.File
import kotlin.math.roundToInt

/**
 * Shows the original photo with the document outline; the four corners can be dragged.
 * A magnifier follows the finger so corners can be placed precisely.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CropEditor(
    source: File,
    imageWidth: Int,
    imageHeight: Int,
    quad: Quad,
    onQuadChange: (Quad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentQuad by rememberUpdatedState(quad)
    val currentOnChange by rememberUpdatedState(onQuadChange)
    var activeCorner by remember { mutableIntStateOf(-1) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val color = if (quad.isConvex()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Leave room around the image so corner handles at the edges stay grabbable.
        val margin = with(density) { 28.dp.toPx() }
        val boxW = constraints.maxWidth - 2 * margin
        val boxH = constraints.maxHeight - 2 * margin
        val aspect = imageWidth.toFloat() / imageHeight
        val (dispW, dispH) = if (aspect > boxW / boxH) boxW to boxW / aspect else boxH * aspect to boxH
        val area = Rect(
            left = margin + (boxW - dispW) / 2,
            top = margin + (boxH - dispH) / 2,
            right = margin + (boxW - dispW) / 2 + dispW,
            bottom = margin + (boxH - dispH) / 2 + dispH,
        )
        val currentArea by rememberUpdatedState(area)

        Box(
            Modifier
                .fillMaxSize()
                .magnifier(
                    sourceCenter = { touch },
                    magnifierCenter = { if (touch.isSpecified) touch - Offset(0f, 110.dp.toPx()) else Offset.Unspecified },
                    zoom = 2.5f,
                    size = DpSize(110.dp, 110.dp),
                    cornerRadius = 55.dp,
                )
                .pointerInput(Unit) {
                    val grabRadius = 48.dp.toPx()
                    detectDragGestures(
                        onDragStart = { start ->
                            val points = currentQuad.toOffsets(currentArea)
                            val nearest = points.indices.minBy { (points[it] - start).getDistance() }
                            activeCorner = if ((points[nearest] - start).getDistance() <= grabRadius) nearest else -1
                            if (activeCorner >= 0) touch = points[activeCorner]
                        },
                        onDragEnd = {
                            activeCorner = -1
                            touch = Offset.Unspecified
                        },
                        onDragCancel = {
                            activeCorner = -1
                            touch = Offset.Unspecified
                        },
                        onDrag = { change, delta ->
                            val index = activeCorner
                            if (index >= 0) {
                                change.consume()
                                val a = currentArea
                                val c = currentQuad.corners[index]
                                val moved = Point2(c.x + delta.x / a.width, c.y + delta.y / a.height).clamped()
                                currentOnChange(currentQuad.withCorner(index, moved))
                                touch = Offset(a.left + moved.x * a.width, a.top + moved.y * a.height)
                            }
                        },
                    )
                },
        ) {
            AsyncImage(
                model = source,
                contentDescription = "Original photo",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                    .clip(MaterialTheme.shapes.extraSmall)
                    .size(with(density) { DpSize(dispW.toDp(), dispH.toDp()) }),
            )
            Canvas(Modifier.fillMaxSize()) {
                drawQuad(
                    quad = quad,
                    area = area,
                    color = color,
                    strokeWidth = 2.5.dp.toPx(),
                    fillAlpha = 0.12f,
                    handleRadius = 11.dp.toPx(),
                    activeHandle = activeCorner,
                )
            }
        }
    }
}
