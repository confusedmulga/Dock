package com.dock.scanner.ui.viewer

import com.dock.scanner.ui.theme.DockColors
import androidx.compose.foundation.shape.CircleShape
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dock.scanner.ui.components.EmptyMessage
import com.dock.scanner.ui.components.GlassBubble
import com.dock.scanner.ui.components.glass
import com.dock.scanner.ui.components.shareDocument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Scrolls through a PDF's pages, rendered on demand at screen width. */
@Composable
fun PdfViewerScreen(uri: Uri, name: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    // PdfRenderer allows one open page at a time, so all rendering is serialized.
    val document = remember(uri) {
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.let(::PdfRenderer) }.getOrNull()
    }
    val lock = remember { Mutex() }
    DisposableEffect(document) {
        // Wait for any page still rendering before closing the file.
        onDispose { CoroutineScope(Dispatchers.IO).launch { lock.withLock { document?.close() } } }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (document == null) {
            EmptyMessage("Can't open this PDF", "It may have been moved or deleted.", Modifier.align(Alignment.Center))
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().hazeSource(hazeState)) {
                val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceAtMost(1600)
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 76.dp, bottom = bottom + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(document.pageCount) { index -> PdfPage(document, lock, index, widthPx) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBubble(hazeState, rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowLeft), "Back", onBack, size = 48.dp)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .glass(hazeState, CircleShape, elevation = 8.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            GlassBubble(hazeState, rememberVectorPainter(Icons.Filled.Share), "Share", { shareDocument(context, uri, "$name.pdf") }, size = 48.dp)
        }
    }
}

@Composable
private fun PdfPage(document: PdfRenderer, lock: Mutex, index: Int, widthPx: Int) {
    val page by produceState<Bitmap?>(null, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    document.openPage(index).use { p ->
                        val height = (widthPx * p.height.toFloat() / p.width).toInt()
                        Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(Color.WHITE)
                            p.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.getOrNull()
            }
        }
    }
    val shape = MaterialTheme.shapes.extraSmall
    val bitmap = page
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap?.let { it.width.toFloat() / it.height } ?: (1 / 1.414f))
            .shadow(3.dp, shape)
            .background(DockColors.Paper, shape),
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
    }
}
