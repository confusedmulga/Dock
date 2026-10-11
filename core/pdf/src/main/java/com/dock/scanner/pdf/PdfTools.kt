package com.dock.scanner.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.IOException
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Thrown for PDFs that need a password; Dock doesn't open those yet. */
class PasswordProtectedException : IOException("This PDF is password-protected")

/**
 * Whole-document PDF operations that keep the original content (text, vectors, fonts)
 * intact. Rendering pages to images is done with Android's PdfRenderer elsewhere.
 *
 * Every function does disk I/O and heavy work: call it off the main thread.
 */
object PdfTools {

    @Volatile private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    /** Large files are paged through a temp file instead of being held in memory. */
    private val memory get() = MemoryUsageSetting.setupMixed(32L * 1024 * 1024)

    private fun open(file: File): PDDocument {
        val doc = try {
            PDDocument.load(file, memory)
        } catch (e: Throwable) {
            // Encrypted PDFs fail inside the security handler (BouncyCastle isn't bundled).
            if (e is NoClassDefFoundError || e.javaClass.simpleName.contains("InvalidPassword")) {
                throw PasswordProtectedException()
            }
            throw e
        }
        if (doc.isEncrypted) {
            doc.close()
            throw PasswordProtectedException()
        }
        return doc
    }

    fun pageCount(file: File): Int = open(file).use { it.numberOfPages }

    /** Appends [inputs] in order into one PDF. */
    fun merge(inputs: List<File>, output: File) {
        require(inputs.size >= 2) { "Merging needs at least two PDFs" }
        inputs.forEach { open(it).close() } // fail early, with a clear error, on protected files
        PDFMergerUtility().apply {
            destinationFileName = output.absolutePath
            inputs.forEach { addSource(it) }
            mergeDocuments(memory)
        }
    }

    /**
     * Writes one PDF per range (1-based, inclusive) via [outputFor]. Pages are copied as they
     * are, so text stays selectable and nothing is re-compressed.
     */
    fun split(input: File, ranges: List<IntRange>, outputFor: (IntRange) -> File) {
        open(input).use { source ->
            val count = source.numberOfPages
            for (range in ranges) {
                require(range.first >= 1 && range.last <= count && !range.isEmpty()) {
                    "Pages ${range.first}-${range.last} are outside 1-$count"
                }
                PDDocument(memory).use { part ->
                    for (n in range) part.importPage(source.getPage(n - 1))
                    part.save(outputFor(range))
                }
            }
        }
    }

    /**
     * @param dpi images are scaled down until they're no sharper than this at the page's size.
     * @param quality JPEG quality, 0..1.
     */
    data class CompressSettings(val dpi: Int, val quality: Float)

    data class CompressResult(val originalBytes: Long, val compressedBytes: Long, val imagesChanged: Int) {
        val savedFraction: Float get() = if (originalBytes == 0L) 0f else 1f - compressedBytes.toFloat() / originalBytes
        val improved: Boolean get() = compressedBytes < originalBytes
    }

    /**
     * Shrinks the images inside a PDF (the bulk of any scanned document): each one is
     * downsampled to [CompressSettings.dpi] at its page size and re-encoded as JPEG, but only
     * replaced if that actually makes it smaller. Text, vectors and fonts are untouched.
     * Transparent images, masks and 1-bit scans are left alone.
     */
    fun compress(input: File, output: File, settings: CompressSettings, onProgress: (Float) -> Unit = {}): CompressResult {
        var changed = 0
        open(input).use { doc ->
            val done = IdentityHashMap<COSBase, PDImageXObject>() // images shared between pages
            val pages = doc.pages.toList()
            pages.forEachIndexed { index, page ->
                val box = page.mediaBox
                val maxSide = ceil(max(box.width, box.height) / 72f * settings.dpi).toInt().coerceAtLeast(64)
                changed += shrinkImages(doc, page.resources ?: return@forEachIndexed, maxSide, settings.quality, done, depth = 0)
                onProgress((index + 1f) / pages.size)
            }
            doc.save(output)
        }
        return CompressResult(input.length(), output.length(), changed)
    }

    private fun shrinkImages(
        doc: PDDocument,
        resources: PDResources,
        maxSide: Int,
        quality: Float,
        done: IdentityHashMap<COSBase, PDImageXObject>,
        depth: Int,
    ): Int {
        if (depth > 8) return 0 // malformed files can nest forms in a loop
        var changed = 0
        for (name in resources.xObjectNames.toList()) {
            val xObject = try { resources.getXObject(name) } catch (_: Exception) { continue }
            when (xObject) {
                is PDFormXObject -> xObject.resources?.let { changed += shrinkImages(doc, it, maxSide, quality, done, depth + 1) }
                is PDImageXObject -> {
                    done[xObject.cosObject]?.let { resources.put(name, it); continue }
                    val replacement = smallerImage(doc, xObject, maxSide, quality)
                    if (replacement != null) {
                        resources.put(name, replacement)
                        done[xObject.cosObject] = replacement
                        changed++
                    } else {
                        done[xObject.cosObject] = xObject
                    }
                }
            }
        }
        return changed
    }

    private fun smallerImage(doc: PDDocument, image: PDImageXObject, maxSide: Int, quality: Float): PDImageXObject? {
        if (image.isStencil || image.bitsPerComponent <= 1) return null
        if (image.softMask != null || image.mask != null) return null
        if (image.cosObject.getItem(COSName.getPDFName("SMaskInData")) != null) return null
        val longSide = max(image.width, image.height)
        if (longSide < 16) return null

        val scale = minOf(1f, maxSide.toFloat() / longSide)
        // Decode at reduced resolution straight away: a 12 MP photo never fully hits memory.
        val subsampling = (1f / scale).toInt().coerceAtLeast(1)
        val decoded = decode(image, subsampling) ?: return null
        val targetW = (image.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (image.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = if (decoded.width == targetW && decoded.height == targetH) decoded else {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also { if (it !== decoded) decoded.recycle() }
        }
        // Decoded bitmaps are ARGB; without this JPEGFactory would attach a useless alpha mask.
        bitmap.setHasAlpha(false)
        return try {
            val jpeg = JPEGFactory.createFromImage(doc, bitmap, quality)
            if (jpeg.cosObject.length < image.cosObject.length) jpeg else null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * JPEGs (scans, photos) are decoded from their original bytes by Android's decoder, which
     * downsamples while decoding. Everything else goes through PdfBox. Returns null for formats
     * Android can't decode (JBIG2, JPEG 2000, CMYK JPEG via PdfBox), which are then left as they are.
     */
    private fun decode(image: PDImageXObject, subsampling: Int): Bitmap? {
        if (image.suffix == "jpg") {
            val sample = Integer.highestOneBit(subsampling)
            runCatching {
                image.stream.createInputStream(listOf(COSName.DCT_DECODE.name)).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()?.let { return it }
        }
        return runCatching { image.getImage(Rect(0, 0, image.width, image.height), subsampling) }.getOrNull()
            ?: runCatching { image.image }.getOrNull()
    }

    /**
     * Parses "1-3, 5, 8-" style page ranges (1-based). An open end means "to the last page".
     * Returns null when the text isn't valid for a document of [pageCount] pages.
     */
    fun parseRanges(text: String, pageCount: Int): List<IntRange>? {
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.map { part ->
            val bounds = part.split('-').map { it.trim() }
            val range = when (bounds.size) {
                1 -> bounds[0].toIntOrNull()?.let { it..it }
                2 -> {
                    val start = bounds[0].ifEmpty { "1" }.toIntOrNull()
                    val end = bounds[1].ifEmpty { pageCount.toString() }.toIntOrNull()
                    if (start != null && end != null) start..end else null
                }
                else -> null
            } ?: return null
            if (range.first < 1 || range.last > pageCount || range.isEmpty()) return null
            range
        }
    }
}
