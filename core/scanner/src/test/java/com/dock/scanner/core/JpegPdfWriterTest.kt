package com.dock.scanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class JpegPdfWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** SOI, an APP0 segment to skip, SOF0 with the given size, EOI. Enough for the header parser. */
    private fun fakeJpeg(width: Int, height: Int, components: Int): ByteArray {
        val app0 = bytes(0xFF, 0xE0, 0x00, 0x06, 'J', 'F', 'I', 'F')
        val sof = bytes(
            0xFF, 0xC0, 0x00, 8 + 3 * components, 0x08,
            height shr 8, height and 0xFF, width shr 8, width and 0xFF, components,
        ) + ByteArray(3 * components)
        return bytes(0xFF, 0xD8) + app0 + sof + bytes(0xFF, 0xD9)
    }

    private fun bytes(vararg values: Any): ByteArray =
        values.map { if (it is Char) it.code.toByte() else (it as Int).toByte() }.toByteArray()

    @Test
    fun readsJpegHeader() {
        val info = JpegPdfWriter.readJpegInfo(fakeJpeg(2480, 3508, 3))
        assertEquals(JpegPdfWriter.JpegInfo(2480, 3508, 3), info)
    }

    @Test
    fun writesValidXrefOffsets() {
        val files = listOf(fakeJpeg(1000, 1414, 3), fakeJpeg(1600, 1000, 1)).mapIndexed { i, bytes ->
            tmp.newFile("p$i.jpg").apply { writeBytes(bytes) }
        }
        val out = ByteArrayOutputStream()
        JpegPdfWriter().write(files, out)
        val pdf = out.toByteArray()
        val text = String(pdf, Charsets.ISO_8859_1)

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.endsWith("%%EOF\n"))
        assertTrue(text.contains("/Count 2"))
        assertTrue(text.contains("/ColorSpace /DeviceGray"))
        // Portrait page: short side mapped to A4 width.
        assertTrue(text.contains("/MediaBox [0 0 595.28 841.73]"))

        val startXref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertTrue(text.startsWith("xref\n", startXref))
        val entries = text.substring(startXref).lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertEquals(9, entries.size)
        entries.forEachIndexed { i, line ->
            val offset = line.substring(0, 10).toInt()
            assertTrue("object ${i + 1} at $offset", text.startsWith("${i + 1} 0 obj", offset))
        }
    }

    @Test
    fun placesSeveralImagesOnOneSheet() {
        val front = tmp.newFile("front.jpg").apply { writeBytes(fakeJpeg(800, 500, 3)) }
        val back = tmp.newFile("back.jpg").apply { writeBytes(fakeJpeg(800, 500, 1)) }
        val sheet = JpegPdfWriter.PageSpec(
            595.28f, 841.89f,
            listOf(
                JpegPdfWriter.PlacedImage(front, 100f, 500f, 400f, 250f),
                JpegPdfWriter.PlacedImage(back, 100f, 150f, 400f, 250f),
            ),
        )
        val out = ByteArrayOutputStream()
        JpegPdfWriter().writePages(listOf(sheet), out)
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)

        assertTrue(text.contains("/Count 1"))
        assertTrue(text.contains("/Im0 6 0 R /Im1 7 0 R"))
        assertTrue(text.contains("400 0 0 250 100 500 cm\n/Im0 Do"))
        assertTrue(text.contains("400 0 0 250 100 150 cm\n/Im1 Do"))

        // Every xref entry must point at its object, even though the page tree is written last.
        val startXref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        val entries = text.substring(startXref).lines().drop(3).takeWhile { it.endsWith(" n ") }
        assertEquals(7, entries.size)
        entries.forEachIndexed { i, line ->
            assertTrue("object ${i + 1}", text.startsWith("${i + 1} 0 obj", line.substring(0, 10).toInt()))
        }
    }
}
