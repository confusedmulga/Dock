package com.dock.scanner.core

import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Minimal PDF writer that embeds JPEG files untouched (DCTDecode), one image per page.
 *
 * Android's PdfDocument re-encodes bitmaps losslessly and produces very large files;
 * passing the JPEG bytes straight through keeps PDFs small and export fast.
 */
class JpegPdfWriter(private val producer: String = "Dock") {

    data class JpegInfo(val width: Int, val height: Int, val components: Int)

    /** Page size in PDF points (1/72 inch) for an image. Default: short side = A4 width. */
    fun interface PageSizer {
        fun size(info: JpegInfo): Pair<Float, Float>
    }

    fun write(jpegs: List<File>, out: OutputStream, sizer: PageSizer = FIT_A4_WIDTH) {
        require(jpegs.isNotEmpty()) { "A PDF needs at least one page" }
        val o = CountingOutputStream(BufferedOutputStream(out, 64 * 1024))
        val total = 3 + 3 * jpegs.size
        val offsets = LongArray(total + 1)

        fun obj(n: Int, body: String) {
            offsets[n] = o.count
            o.ascii("$n 0 obj\n$body\nendobj\n")
        }

        o.ascii("%PDF-1.4\n")
        o.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        val kids = jpegs.indices.joinToString(" ") { "${pageObj(it)} 0 R" }
        obj(2, "<< /Type /Pages /Kids [$kids] /Count ${jpegs.size} >>")
        obj(3, "<< /Producer (${escape(producer)}) /CreationDate (D:${timestamp()}Z) >>")

        jpegs.forEachIndexed { i, file ->
            val bytes = file.readBytes()
            val info = readJpegInfo(bytes)
            val (w, h) = sizer.size(info)
            val pw = num(w)
            val ph = num(h)

            obj(
                pageObj(i),
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pw $ph] " +
                    "/Resources << /XObject << /Im0 ${pageObj(i) + 2} 0 R >> /ProcSet [/PDF /ImageB /ImageC] >> " +
                    "/Contents ${pageObj(i) + 1} 0 R >>",
            )

            val content = "q\n$pw 0 0 $ph 0 0 cm\n/Im0 Do\nQ\n"
            obj(pageObj(i) + 1, "<< /Length ${content.length} >>\nstream\n${content}endstream")

            val colorSpace = when (info.components) {
                1 -> "/DeviceGray"
                3 -> "/DeviceRGB"
                4 -> "/DeviceCMYK /Decode [1 0 1 0 1 0 1 0]"
                else -> throw IllegalArgumentException("Unsupported JPEG with ${info.components} components")
            }
            offsets[pageObj(i) + 2] = o.count
            o.ascii(
                "${pageObj(i) + 2} 0 obj\n<< /Type /XObject /Subtype /Image /Width ${info.width} " +
                    "/Height ${info.height} /ColorSpace $colorSpace /BitsPerComponent 8 " +
                    "/Filter /DCTDecode /Length ${bytes.size} >>\nstream\n",
            )
            o.write(bytes)
            o.ascii("\nendstream\nendobj\n")
        }

        val xref = o.count
        val sb = StringBuilder("xref\n0 ${total + 1}\n0000000000 65535 f \n")
        for (n in 1..total) sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets[n]))
        sb.append("trailer\n<< /Size ${total + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xref\n%%EOF\n")
        o.ascii(sb.toString())
        o.flush()
    }

    private fun pageObj(index: Int) = 4 + 3 * index

    private fun num(v: Float): String =
        String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        fun ascii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    }

    companion object {
        const val A4_WIDTH_PT = 595.28f

        val FIT_A4_WIDTH = PageSizer { info ->
            val scale = A4_WIDTH_PT / minOf(info.width, info.height)
            info.width * scale to info.height * scale
        }

        /** Reads dimensions and component count from a JPEG's SOF marker. */
        fun readJpegInfo(bytes: ByteArray): JpegInfo {
            fun u8(i: Int) = bytes[i].toInt() and 0xFF
            fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)

            require(bytes.size > 4 && u8(0) == 0xFF && u8(1) == 0xD8) { "Not a JPEG" }
            var i = 2
            while (i + 3 < bytes.size) {
                if (u8(i) != 0xFF) { i++; continue }
                val marker = u8(i + 1)
                when {
                    marker == 0xFF -> { i++; continue } // fill byte
                    marker == 0x01 || marker in 0xD0..0xD7 -> { i += 2; continue }
                    marker == 0xD9 || marker == 0xDA -> break // EOI / start of scan before any SOF
                }
                val length = u16(i + 2)
                val isSof = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
                if (isSof) {
                    return JpegInfo(width = u16(i + 7), height = u16(i + 5), components = u8(i + 9))
                }
                i += 2 + length
            }
            throw IllegalArgumentException("JPEG has no frame header")
        }
    }
}
