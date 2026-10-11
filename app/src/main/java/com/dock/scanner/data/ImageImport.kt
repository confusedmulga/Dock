package com.dock.scanner.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlin.math.max

object ImageImport {

    /**
     * Copies a picked image (any format the platform decodes, e.g. HEIC/PNG) into [dest] as an
     * upright JPEG no larger than [maxSide], so the processing pipeline only ever sees one kind of input.
     */
    fun copyAsUprightJpeg(resolver: ContentResolver, uri: Uri, dest: File, maxSide: Int = 4096) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Could not decode image")

        val degrees = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val upright = if (degrees == 0) decoded else {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { decoded.recycle() }
        }
        try {
            dest.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } finally {
            upright.recycle()
        }
    }
}
