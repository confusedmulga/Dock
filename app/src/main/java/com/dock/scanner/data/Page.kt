package com.dock.scanner.data

import com.dock.scanner.core.PageFilter
import com.dock.scanner.core.Quad
import java.io.File

data class Page(
    val id: String,
    /** Orders pages by when they were added, even if their processing finishes out of order. */
    val sequence: Long,
    /** Original photo. May carry an EXIF orientation; [sourceWidth]/[sourceHeight] are upright. */
    val source: File,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val quad: Quad,
    /** What edge detection found, so the user can go back to it after manual edits. */
    val detectedQuad: Quad?,
    val filter: PageFilter,
    /** Clockwise, applied after cropping: 0, 90, 180 or 270. */
    val rotation: Int,
    /** Finished page image; null until the first render completes. */
    val output: File?,
    val processing: Boolean,
    val revision: Int,
)
