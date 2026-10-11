package com.dock.scanner.ui.tools

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dock.scanner.R
import com.dock.scanner.data.storage.LibraryItem
import com.dock.scanner.data.storage.PdfPreviews
import com.dock.scanner.data.tools.PickPdfs
import com.dock.scanner.data.tools.SourcePdf
import com.dock.scanner.pdf.PdfTools
import com.dock.scanner.ui.components.LargeTitle
import com.dock.scanner.ui.components.PdfThumbnail
import com.dock.scanner.ui.components.SectionHeader
import com.dock.scanner.ui.components.shareDocument
import com.dock.scanner.ui.theme.ToolAccent
import com.dock.scanner.ui.theme.toolAccent
import kotlin.math.roundToInt

private data class ToolInfo(val title: String, val description: String, val action: String, val accent: ToolAccent, val multiple: Boolean)

private fun PdfTool.info() = when (this) {
    PdfTool.Compress -> ToolInfo("Compress PDF", "Shrinks the pictures inside a PDF. Text stays sharp and selectable.", "Compress", ToolAccent.Compress, false)
    PdfTool.ToImages -> ToolInfo("PDF to images", "Every page becomes a picture. Each PDF gets its own folder.", "Convert", ToolAccent.ToImages, true)
    PdfTool.Merge -> ToolInfo("Merge PDFs", "Combine PDFs into one, in the order shown.", "Merge", ToolAccent.Merge, true)
    PdfTool.Split -> ToolInfo("Split PDF", "Save pages or page ranges as separate PDFs.", "Split", ToolAccent.Split, false)
}

@Composable
fun ToolScreen(
    vm: ToolsViewModel,
    initialUri: () -> Uri?,
    onBack: () -> Unit,
    onOpenPdf: (LibraryItem) -> Unit,
    onShowInShelf: (LibraryItem) -> Unit,
) {
    val context = LocalContext.current
    val tool by vm.tool.collectAsStateWithLifecycle()
    val inputs by vm.inputs.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val loading by vm.loadingInputs.collectAsStateWithLifecycle()
    val info = tool.info()
    val accent = toolAccent(info.accent)

    val pick = rememberLauncherForActivityResult(PickPdfs(initialUri, info.multiple)) { vm.addInputs(it) }
    // Straight to the picker when the tool was opened from Home with nothing chosen yet.
    var autoPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A preset file from the library may still be loading; only open the picker when there's none.
        if (!autoPicked && vm.inputs.value.isEmpty() && !vm.loadingInputs.value) pick.launch(Unit)
        autoPicked = true
    }

    // Per-tool options.
    var preset by rememberSaveable { mutableStateOf(CompressPreset.Balanced) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var dpi by rememberSaveable { mutableFloatStateOf(120f) }
    var quality by rememberSaveable { mutableFloatStateOf(0.65f) }
    var format by rememberSaveable { mutableStateOf(ImageFormat.JPG) }
    var imageDpi by rememberSaveable { mutableStateOf(150) }
    var mergeName by rememberSaveable { mutableStateOf("") }
    var everyPage by rememberSaveable { mutableStateOf(true) }
    var ranges by rememberSaveable { mutableStateOf("") }

    val working = state is ToolState.Working
    val parsedRanges = inputs.firstOrNull()?.let { PdfTools.parseRanges(ranges, it.pages) }
    val ready = !working && !loading && when (tool) {
        PdfTool.Merge -> inputs.size >= 2 && mergeName.isNotBlank() && mergeName.none { it in "/\\:*?\"<>|" }
        PdfTool.Split -> inputs.size == 1 && (everyPage || parsedRanges != null)
        else -> inputs.isNotEmpty()
    }
    LaunchedEffect(inputs.firstOrNull()?.uri) {
        if (mergeName.isBlank()) inputs.firstOrNull()?.let { mergeName = "${it.name} merged" }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()
    // Bring the result into view; it appears at the bottom, under the action bar otherwise.
    LaunchedEffect(state) {
        if (state is ToolState.Done || state is ToolState.Failed) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(top = top, bottom = 140.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Column {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", modifier = Modifier.size(30.dp))
                    }
                    LargeTitle(info.title, subtitle = info.description)
                }
            }
            item(key = "inputs-title") { SectionHeader(if (info.multiple) "PDFs" else "PDF") }
            itemsIndexed(inputs, key = { i, it -> "${it.uri}#$i" }) { i, input ->
                InputRow(
                    input = input,
                    index = i,
                    count = inputs.size,
                    reorderable = tool == PdfTool.Merge,
                    enabled = !working,
                    onRemove = { vm.remove(i) },
                    onMove = { to -> vm.move(i, to) },
                )
            }
            item(key = "add") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pick.launch(Unit) }, enabled = !working && !loading) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                info.multiple -> "Add PDFs"
                                inputs.isEmpty() -> "Choose a PDF"
                                else -> "Choose another PDF"
                            },
                        )
                    }
                    if (loading) {
                        Spacer(Modifier.width(12.dp))
                        Text("Reading…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "options") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    when (tool) {
                        PdfTool.Compress -> CompressOptions(
                            preset = preset, custom = custom, dpi = dpi, quality = quality,
                            onPreset = { preset = it; custom = false },
                            onCustom = { custom = true },
                            onDpi = { dpi = it }, onQuality = { quality = it },
                        )
                        PdfTool.ToImages -> ImageOptions(format, imageDpi, onFormat = { format = it }, onDpi = { imageDpi = it })
                        PdfTool.Merge -> {
                            SectionHeader("Name", start = 4.dp)
                            OutlinedTextField(
                                value = mergeName,
                                onValueChange = { mergeName = it },
                                singleLine = true,
                                suffix = { Text(".pdf") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PdfTool.Split -> SplitOptions(
                            everyPage = everyPage,
                            ranges = ranges,
                            pageCount = inputs.firstOrNull()?.pages,
                            valid = parsedRanges != null,
                            onEveryPage = { everyPage = it },
                            onRanges = { ranges = it },
                        )
                    }
                }
            }
            item(key = "result") {
                AnimatedVisibility(
                    visible = state is ToolState.Done || state is ToolState.Failed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    when (val s = state) {
                        is ToolState.Done -> ResultCard(
                            output = s.output,
                            onOpen = { s.output.pdf?.let(onOpenPdf) },
                            onShare = { s.output.pdf?.let { shareDocument(context, it.uri, it.name) } },
                            onShowFolder = { s.output.folder?.let(onShowInShelf) },
                        )
                        is ToolState.Failed -> ErrorCard(s.message)
                        else -> Spacer(Modifier.height(0.dp))
                    }
                }
            }
        }

        // The action bar: the primary button, or progress while a tool runs.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge.copy(
                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
            ),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                val s = state
                if (s is ToolState.Working) {
                    Text("${s.label}…", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    val p = s.progress
                    if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                } else {
                    Button(
                        onClick = {
                            when (tool) {
                                PdfTool.Compress -> vm.compress(
                                    if (custom) PdfTools.CompressSettings(dpi.roundToInt(), quality) else preset.settings,
                                )
                                PdfTool.ToImages -> vm.toImages(imageDpi, format)
                                PdfTool.Merge -> vm.merge(mergeName.trim())
                                PdfTool.Split -> vm.split(if (everyPage) null else parsedRanges)
                            }
                        },
                        enabled = ready,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = accent.onContainer,
                            contentColor = accent.container,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(info.action, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    input: SourcePdf,
    index: Int,
    count: Int,
    reorderable: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val context = LocalContext.current
    val widthPx = with(LocalDensity.current) { 80.dp.roundToPx() }
    val preview by produceState(PdfPreviews.cached(input.uri, input.size), input.uri) {
        if (value == null) value = PdfPreviews.load(context, input.uri, input.size, widthPx)
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (reorderable) {
            Text("${index + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
        }
        PdfThumbnail(preview?.thumbnail, Modifier.size(width = 40.dp, height = 54.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(input.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${if (input.pages == 1) "1 page" else "${input.pages} pages"} · ${Formatter.formatShortFileSize(context, input.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (reorderable) {
            IconButton(onClick = { onMove(index - 1) }, enabled = enabled && index > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = { onMove(index + 1) }, enabled = enabled && index < count - 1) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
        IconButton(onClick = onRemove, enabled = enabled) { Icon(Icons.Filled.Close, contentDescription = "Remove ${input.name}") }
    }
}

@Composable
private fun SelectableRow(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompressOptions(
    preset: CompressPreset,
    custom: Boolean,
    dpi: Float,
    quality: Float,
    onPreset: (CompressPreset) -> Unit,
    onCustom: () -> Unit,
    onDpi: (Float) -> Unit,
    onQuality: (Float) -> Unit,
) {
    SectionHeader("Compression", start = 4.dp)
    CompressPreset.entries.forEach { p ->
        SelectableRow(selected = !custom && p == preset, title = p.label, detail = p.detail, onClick = { onPreset(p) })
    }
    SelectableRow(selected = custom, title = "Custom", detail = "Choose resolution and quality yourself", onClick = onCustom)
    AnimatedVisibility(visible = custom) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            OptionSlider("Resolution", "${dpi.roundToInt()} dpi", dpi, 50f..300f, onDpi)
            OptionSlider("Quality", "${(quality * 100).roundToInt()}%", quality, 0.2f..0.95f, onQuality)
        }
    }
}

@Composable
private fun OptionSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
        Slider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).width(56.dp))
    }
}

@Composable
private fun ImageOptions(format: ImageFormat, dpi: Int, onFormat: (ImageFormat) -> Unit, onDpi: (Int) -> Unit) {
    SectionHeader("Format", start = 4.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImageFormat.entries.forEach { f -> FilterChip(selected = f == format, onClick = { onFormat(f) }, label = { Text(f.label) }) }
    }
    SectionHeader("Resolution", start = 4.dp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(72 to "Small · 72 dpi", 150 to "Standard · 150 dpi", 300 to "Print · 300 dpi").forEach { (value, label) ->
            FilterChip(selected = value == dpi, onClick = { onDpi(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SplitOptions(
    everyPage: Boolean,
    ranges: String,
    pageCount: Int?,
    valid: Boolean,
    onEveryPage: (Boolean) -> Unit,
    onRanges: (String) -> Unit,
) {
    SectionHeader("Split into", start = 4.dp)
    SelectableRow(everyPage, "Every page", "One PDF per page", onClick = { onEveryPage(true) })
    SelectableRow(!everyPage, "Page ranges", "One PDF per range you type", onClick = { onEveryPage(false) })
    AnimatedVisibility(visible = !everyPage) {
        OutlinedTextField(
            value = ranges,
            onValueChange = onRanges,
            singleLine = true,
            label = { Text("Pages") },
            placeholder = { Text("e.g. 1-3, 5, 8-") },
            isError = ranges.isNotBlank() && !valid,
            supportingText = {
                Text(
                    when {
                        pageCount == null -> "Choose a PDF first"
                        ranges.isNotBlank() && !valid -> "Use page numbers from 1 to $pageCount, like 1-3, 5"
                        pageCount < 2 -> "This PDF has only 1 page."
                        else -> "This PDF has $pageCount pages. “2-” means page 2 to the end."
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun ResultCard(output: ToolOutput, onOpen: () -> Unit, onShare: () -> Unit, onShowFolder: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(output.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(output.detail, style = MaterialTheme.typography.bodyMedium)
            if (output.pdf != null || output.folder != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (output.pdf != null) {
                        Button(onClick = onOpen) { Text("Open") }
                        TextButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                    if (output.folder != null) {
                        Button(onClick = onShowFolder) {
                            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Show in Shelf")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
