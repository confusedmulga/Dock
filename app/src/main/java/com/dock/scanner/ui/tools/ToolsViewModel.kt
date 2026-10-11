package com.dock.scanner.ui.tools

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dock.scanner.DockApp
import com.dock.scanner.data.storage.DockLibrary
import com.dock.scanner.data.storage.LibraryItem
import com.dock.scanner.data.tools.PdfSources
import com.dock.scanner.data.tools.SourcePdf
import com.dock.scanner.pdf.PasswordProtectedException
import com.dock.scanner.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class PdfTool { Compress, ToImages, Merge, Split }

enum class ImageFormat(val label: String, val ext: String, val mime: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
}

/** Something a tool produced, for the result card. */
data class ToolOutput(val title: String, val detail: String, val pdf: LibraryItem? = null, val folder: LibraryItem? = null)

sealed interface ToolState {
    data object Idle : ToolState
    data class Working(val label: String, val progress: Float?) : ToolState
    data class Done(val output: ToolOutput) : ToolState
    data class Failed(val message: String) : ToolState
}

/**
 * Runs the PDF tools. Inputs come from the system picker or the library; results are written
 * into the Dock folder (next to the input when it came from the library), never overwriting it.
 */
class ToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val library: DockLibrary = (app as DockApp).library
    private val resolver = app.contentResolver

    private val _tool = MutableStateFlow(PdfTool.Compress)
    val tool: StateFlow<PdfTool> = _tool.asStateFlow()

    private val _inputs = MutableStateFlow<List<SourcePdf>>(emptyList())
    val inputs: StateFlow<List<SourcePdf>> = _inputs.asStateFlow()

    private val _state = MutableStateFlow<ToolState>(ToolState.Idle)
    val state: StateFlow<ToolState> = _state.asStateFlow()

    private val _loadingInputs = MutableStateFlow(false)
    val loadingInputs: StateFlow<Boolean> = _loadingInputs.asStateFlow()

    private var job: Job? = null

    /** Opens a tool, optionally with a file from the library already chosen. */
    fun start(tool: PdfTool, preset: LibraryItem? = null) {
        job?.cancel()
        _tool.value = tool
        _inputs.value = emptyList()
        _state.value = ToolState.Idle
        if (preset != null) addInputs(listOf(preset.uri), preset.parentId)
    }

    fun addInputs(uris: List<Uri>, parentId: String? = null) {
        if (uris.isEmpty()) return
        _loadingInputs.value = true
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { PdfSources.describe(resolver, uri, parentId) }
                        .onFailure {
                            _state.value = ToolState.Failed(
                                if (it is PasswordProtectedException) "Password-protected PDFs aren't supported yet" else "Couldn't read a PDF",
                            )
                        }
                        .getOrNull()
                }
            }
            val single = _tool.value == PdfTool.Compress || _tool.value == PdfTool.Split
            _inputs.value = if (single) added.takeLast(1).ifEmpty { _inputs.value } else _inputs.value + added
            if (_state.value is ToolState.Done) _state.value = ToolState.Idle
            _loadingInputs.value = false
        }
    }

    fun remove(index: Int) {
        _inputs.value = _inputs.value.filterIndexed { i, _ -> i != index }
        _state.value = ToolState.Idle
    }

    fun move(from: Int, to: Int) {
        val list = _inputs.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _inputs.value = list
    }

    fun dismissResult() {
        _state.value = ToolState.Idle
    }

    // region Tools

    fun compress(settings: PdfTools.CompressSettings) = launchTool("Compressing") { root ->
        val input = _inputs.value.single()
        val inFile = PdfSources.copyToCache(context, input.uri, "in.pdf")
        val outFile = File(inFile.parentFile, "out.pdf")
        val result = PdfTools.compress(inFile, outFile, settings) { progress(it * 0.9f) }
        val before = size(result.originalBytes)
        if (!result.improved || result.savedFraction < 0.01f) {
            return@launchTool ToolOutput("Already as small as it gets", "$before. This PDF's images are already compressed at these settings.")
        }
        val parent = input.parentId ?: library.rootId(root)
        val pdf = savePdf(root, parent, "${input.name} (compressed)", outFile)
        ToolOutput(
            "Saved “${pdf.displayName}”",
            "$before → ${size(result.compressedBytes)}  (−${(result.savedFraction * 100).roundToInt()}%)",
            pdf = pdf,
        )
    }

    fun merge(name: String) = launchTool("Merging") { root ->
        val files = _inputs.value.mapIndexed { i, input -> PdfSources.copyToCache(context, input.uri, "merge_$i.pdf") }
        progress(0.4f)
        val out = File(files.first().parentFile, "merged.pdf")
        PdfTools.merge(files, out)
        progress(0.9f)
        val pdf = savePdf(root, library.rootId(root), name, out)
        ToolOutput("Saved “${pdf.displayName}”", "${_inputs.value.size} PDFs · ${_inputs.value.sumOf { it.pages }} pages · ${size(out.length())}", pdf = pdf)
    }

    /** [ranges] null = every page on its own. */
    fun split(ranges: List<IntRange>?) = launchTool("Splitting") { root ->
        val input = _inputs.value.single()
        val inFile = PdfSources.copyToCache(context, input.uri, "in.pdf")
        val parts = ranges ?: (1..input.pages).map { it..it }
        val folder = newFolder(root, input.parentId ?: library.rootId(root), "${input.name} (split)")
        val tmpDir = File(inFile.parentFile, "split").apply { deleteRecursively(); mkdirs() }
        PdfTools.split(inFile, parts) { range -> File(tmpDir, "${range.first}-${range.last}.pdf") }
        parts.forEachIndexed { i, range ->
            val label = if (range.first == range.last) "p${range.first}" else "p${range.first}-${range.last}"
            copyInto(root, folder.documentId, "${input.name} $label.pdf", DockLibrary.MIME_PDF, File(tmpDir, "${range.first}-${range.last}.pdf"))
            progress(0.5f + 0.5f * (i + 1) / parts.size)
        }
        ToolOutput("Split into ${parts.size} PDFs", "Saved in the folder “${folder.name}”", folder = folder)
    }

    /** Each PDF gets its own folder of page images, named after it. */
    fun toImages(dpi: Int, format: ImageFormat) = launchTool("Converting") { root ->
        val inputs = _inputs.value
        val total = inputs.sumOf { it.pages }.coerceAtLeast(1)
        var done = 0
        var lastFolder: LibraryItem? = null
        for (input in inputs) {
            val folder = newFolder(root, input.parentId ?: library.rootId(root), "${input.name} (images)")
            lastFolder = folder
            PdfSources.withRenderer(resolver, input.uri) { renderer ->
                val digits = renderer.pageCount.toString().length
                for (i in 0 until renderer.pageCount) {
                    val (bitmap, _) = PdfSources.render(renderer, i, dpi)
                    val uri = library.createFile(root, folder.documentId, format.mime, "${input.name} ${(i + 1).toString().padStart(digits, '0')}.${format.ext}")
                        ?: error("Couldn't create an image file")
                    try {
                        resolver.openOutputStream(uri, "wt")!!.use {
                            bitmap.compress(if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    done++
                    progress(done.toFloat() / total)
                }
            }
        }
        val pages = if (total == 1) "1 image" else "$total images"
        if (inputs.size == 1) {
            ToolOutput("Converted to $pages", "Saved in the folder “${lastFolder!!.name}”", folder = lastFolder)
        } else {
            ToolOutput("Converted to $pages", "One folder per PDF, named after each file", folder = lastFolder)
        }
    }

    // endregion

    private val context get() = getApplication<Application>()

    private fun progress(value: Float) {
        (_state.value as? ToolState.Working)?.let { _state.value = it.copy(progress = value.coerceIn(0f, 1f)) }
    }

    private fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    private fun launchTool(label: String, block: suspend (Uri) -> ToolOutput) {
        if (_state.value is ToolState.Working || _inputs.value.isEmpty()) return
        val root = library.rootUri() ?: run {
            _state.value = ToolState.Failed("Choose a storage folder first")
            return
        }
        _state.value = ToolState.Working(label, 0f)
        job = viewModelScope.launch {
            _state.value = try {
                ToolState.Done(withContext(Dispatchers.IO) { block(root) })
            } catch (e: PasswordProtectedException) {
                ToolState.Failed("Password-protected PDFs aren't supported yet")
            } catch (e: Exception) {
                ToolState.Failed(e.message ?: "Something went wrong")
            } finally {
                File(context.cacheDir, "tools").deleteRecursively()
            }
        }
    }

    private fun savePdf(root: Uri, parentId: String, name: String, file: File): LibraryItem {
        val uri = copyInto(root, parentId, "$name.pdf", DockLibrary.MIME_PDF, file)
        return item(root, uri, parentId, isFolder = false)
    }

    private fun copyInto(root: Uri, parentId: String, fileName: String, mime: String, file: File): Uri {
        val uri = library.createFile(root, parentId, mime, fileName) ?: error("Couldn't create $fileName")
        resolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        return uri
    }

    private fun newFolder(root: Uri, parentId: String, name: String): LibraryItem {
        val uri = library.createFolder(root, parentId, name) ?: error("Couldn't create the folder")
        return item(root, uri, parentId, isFolder = true)
    }

    /** Describes something just created, with the name the provider actually gave it. */
    private fun item(root: Uri, uri: Uri, parentId: String, isFolder: Boolean): LibraryItem {
        val id = library.idOf(uri)
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: id.substringAfterLast('/')
        return LibraryItem(
            uri = library.documentUri(root, id),
            documentId = id,
            parentId = parentId,
            name = name,
            isFolder = isFolder,
            lastModified = System.currentTimeMillis(),
            size = 0,
            mimeType = if (isFolder) android.provider.DocumentsContract.Document.MIME_TYPE_DIR else DockLibrary.MIME_PDF,
        )
    }
}

/** Compression presets, like the usual "extreme / recommended / less" choices. */
enum class CompressPreset(val label: String, val detail: String, val settings: PdfTools.CompressSettings) {
    Strong("Smallest file", "72 dpi · lower quality", PdfTools.CompressSettings(72, 0.45f)),
    Balanced("Recommended", "120 dpi · good quality", PdfTools.CompressSettings(120, 0.65f)),
    Light("Best quality", "200 dpi · high quality", PdfTools.CompressSettings(200, 0.8f)),
}
