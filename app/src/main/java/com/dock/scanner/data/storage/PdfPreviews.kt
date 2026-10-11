package com.dock.scanner.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** First-page thumbnail plus page count of a PDF. */
class PdfPreview(val thumbnail: Bitmap, val pageCount: Int)

/** Renders and caches small first-page previews for list rows. */
object PdfPreviews {

    private val cache = object : LruCache<String, PdfPreview>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: PdfPreview) = value.thumbnail.byteCount
    }

    /** Opening many PDFs at once while flinging a long list would only queue up work. */
    private val gate = Semaphore(2)

    fun cached(uri: Uri, lastModified: Long): PdfPreview? = cache.get(key(uri, lastModified))

    suspend fun load(context: Context, uri: Uri, lastModified: Long, widthPx: Int): PdfPreview? {
        val key = key(uri, lastModified)
        cache.get(key)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            renderer.openPage(0).use { page ->
                                val height = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                PdfPreview(bitmap, renderer.pageCount)
                            }
                        }
                    }
                }.getOrNull()?.also { cache.put(key, it) }
            }
        }
    }

    private fun key(uri: Uri, lastModified: Long) = "$uri@$lastModified"
}
