package com.dock.scanner.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dock.scanner.core.DocumentDetector
import com.dock.scanner.core.JpegPdfWriter
import com.dock.scanner.core.PageFilter
import com.dock.scanner.core.PageProcessor
import com.dock.scanner.core.Quad
import com.dock.scanner.data.ImageImport
import com.dock.scanner.data.Page
import com.dock.scanner.data.storage.DockLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

/**
 * Holds the current scan session: the pages captured so far and their processing state.
 *
 * The session is a draft: it lives until it's saved as a PDF into the library or discarded.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionDir = File(app.filesDir, "session").apply {
        deleteRecursively()
        mkdirs()
    }

    /** Two pages at a time: keeps the UI responsive and memory bounded with 12 MP photos. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.Default.limitedParallelism(2)
    private val sequence = AtomicLong()

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun newCaptureFile(): File = File(sessionDir, "src_${UUID.randomUUID()}.jpg")

    /** Adds a photo that is already on disk (camera capture or import), detects edges and renders it. */
    fun addPhoto(file: File, seq: Long = sequence.incrementAndGet()) {
        viewModelScope.launch {
            val page = withContext(worker) {
                runCatching {
                    val (w, h) = PageProcessor.uprightSize(file)
                    val detected = runCatching { PageProcessor.detect(file, DocumentDetector()) }.getOrNull()
                    Page(
                        id = UUID.randomUUID().toString(),
                        sequence = seq,
                        source = file,
                        sourceWidth = w,
                        sourceHeight = h,
                        quad = detected ?: Quad.FULL,
                        detectedQuad = detected,
                        filter = PageFilter.AUTO,
                        rotation = 0,
                        output = null,
                        processing = true,
                        revision = 0,
                    )
                }.getOrNull()
            }
            if (page == null) {
                file.delete()
                message("Couldn't read that image")
                return@launch
            }
            _pages.update { (it + page).sortedBy(Page::sequence) }
            render(page.id)
        }
    }

    fun importImages(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        for (uri in uris) {
            val seq = sequence.incrementAndGet()
            viewModelScope.launch {
                val file = newCaptureFile()
                val ok = withContext(Dispatchers.IO) {
                    runCatching { ImageImport.copyAsUprightJpeg(resolver, uri, file) }.isSuccess
                }
                if (ok) addPhoto(file, seq) else message("Couldn't import an image")
            }
        }
    }

    fun setQuad(id: String, quad: Quad) = edit(id) { it.copy(quad = quad) }

    fun setFilter(id: String, filter: PageFilter) = edit(id) { it.copy(filter = filter) }

    fun rotateClockwise(id: String) = edit(id) { it.copy(rotation = (it.rotation + 90) % 360) }

    fun delete(id: String) {
        val page = find(id) ?: return
        _pages.update { list -> list.filterNot { it.id == id } }
        page.source.delete()
        page.output?.delete()
    }

    fun clear() {
        val old = _pages.value
        _pages.value = emptyList()
        old.forEach {
            it.source.delete()
            it.output?.delete()
        }
    }

    fun suggestedFileName(): String =
        "Scan ${SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())}"

    /** Folder that was open on the Shelf when the scan started; the save sheet preselects it. */
    var targetFolderId: String? = null

    /**
     * Writes the session as a PDF into the library folder [parentId], then clears the session.
     * The PDF is created first and removed again if writing fails, so no empty files are left behind.
     */
    fun saveToLibrary(library: DockLibrary, root: Uri, parentId: String, name: String, onSaved: (String) -> Unit) {
        if (_exporting.value || _pages.value.isEmpty()) return
        _exporting.value = true
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            try {
                // Wait for any filter/crop changes still rendering so the PDF matches the screen.
                val pages = _pages.first { list -> list.none { it.processing } }
                val files = pages.mapNotNull { it.output }
                withContext(Dispatchers.IO) {
                    val uri = library.createPdf(root, parentId, name) ?: error("Couldn't create the file")
                    try {
                        resolver.openOutputStream(uri, "wt")!!.use { JpegPdfWriter().write(files, it) }
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        throw e
                    }
                }
                clear()
                targetFolderId = null
                onSaved(name)
            } catch (e: Exception) {
                message("Couldn't save: ${e.message}")
            } finally {
                _exporting.value = false
            }
        }
    }

    private fun edit(id: String, change: (Page) -> Page) {
        val before = find(id) ?: return
        val after = change(before)
        if (after == before) return
        _pages.update { list -> list.map { if (it.id == id) after else it } }
        render(id)
    }

    /**
     * Re-renders a page. Each request bumps the page's revision; a render that finishes
     * after a newer request was made is thrown away, so rapid filter taps never show stale output.
     */
    private fun render(id: String) {
        var revision = 0
        _pages.update { list ->
            list.map {
                if (it.id != id) it else it.copy(processing = true, revision = it.revision + 1).also { p -> revision = p.revision }
            }
        }
        viewModelScope.launch {
            val out = File(sessionDir, "page_${id}_$revision.jpg")
            val result = withContext(worker) {
                val page = find(id)?.takeIf { it.revision == revision } ?: return@withContext null
                runCatching { PageProcessor.process(page.source, page.quad, page.filter, page.rotation, out) }
            } ?: return@launch

            val current = find(id)
            if (current == null || current.revision != revision) {
                out.delete()
                return@launch
            }
            if (result.isSuccess) {
                _pages.update { list -> list.map { if (it.id == id) it.copy(output = out, processing = false) else it } }
                current.output?.takeIf { it != out }?.delete()
            } else {
                _pages.update { list -> list.map { if (it.id == id) it.copy(processing = false) else it } }
                message("Couldn't process page: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun find(id: String): Page? = _pages.value.firstOrNull { it.id == id }

    private fun message(text: String) {
        _events.trySend(UiEvent.Message(text))
    }
}
