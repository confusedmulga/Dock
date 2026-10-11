package com.dock.scanner.ui.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dock.scanner.R
import com.dock.scanner.core.PageFilter
import com.dock.scanner.core.Quad
import com.dock.scanner.data.Page
import com.dock.scanner.data.Paper
import com.dock.scanner.data.StrokeKind
import com.dock.scanner.ui.ScanViewModel
import com.dock.scanner.ui.theme.DockMotion
import kotlin.math.roundToInt

/** The editor's tools, in toolbar order. */
enum class EditorTool(val label: String, @param:DrawableRes val icon: Int) {
    Retake("Retake", R.drawable.ic_retake),
    Crop("Crop", R.drawable.ic_crop),
    EditText("Edit text", R.drawable.ic_text),
    Filters("Filters", R.drawable.ic_filter),
    Bright("Bright", R.drawable.ic_brightness),
    Eraser("Eraser", R.drawable.ic_eraser),
    Markup("Markup", R.drawable.ic_markup),
    Adjust("Adjust", R.drawable.ic_adjust),
    Reorder("Reorder", R.drawable.ic_reorder),
}

private val PenColors = listOf(
    0xFF111111, 0xFFFFFFFF, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA,
).map { it.toInt() }

@Composable
fun PageEditorScreen(
    vm: ScanViewModel,
    pageId: String,
    onBack: () -> Unit,
    onRetake: (String) -> Unit,
    onReorder: () -> Unit,
) {
    val all by vm.pages.collectAsStateWithLifecycle()
    val sheets by vm.sheets.collectAsStateWithLifecycle()
    val index = sheets.indexOfFirst { it.id == pageId }
    if (index < 0) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val page = sheets[index]
    val pagesById = remember(all) { all.associateBy { it.id } }

    var tool by rememberSaveable { mutableStateOf<EditorTool?>(null) }
    BackHandler(enabled = tool != null) { tool = null }

    // Drafts for live previews; committed to the page when a gesture ends.
    var brightness by remember(page.id) { mutableFloatStateOf(page.brightness) }
    var contrast by remember(page.id) { mutableFloatStateOf(page.contrast) }
    LaunchedEffect(page.brightness, page.contrast) {
        brightness = page.brightness
        contrast = page.contrast
    }
    var eraserColor by rememberSaveable { mutableIntStateOf(android.graphics.Color.WHITE) }
    var eraserWidth by rememberSaveable { mutableFloatStateOf(0.035f) }
    var picking by remember { mutableStateOf(false) }
    var penColor by rememberSaveable { mutableIntStateOf(PenColors[2]) }
    var penWidth by rememberSaveable { mutableFloatStateOf(0.008f) }
    var penOpacity by rememberSaveable { mutableFloatStateOf(1f) }
    var selectedOnSheet by remember(page.id) { mutableStateOf<String?>(page.id) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        // Top bar: where am I, how do I get out.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (tool != null) tool = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", modifier = Modifier.size(30.dp))
            }
            Text(
                if (tool == EditorTool.Crop) "Adjust corners" else "Page ${index + 1} of ${sheets.size}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (page.processing) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            if (tool == null) {
                IconButton(onClick = {
                    vm.delete(page.id)
                    onBack()
                }) { Icon(Icons.Filled.Delete, contentDescription = "Delete page") }
            }
        }

        if (tool == EditorTool.Crop) {
            CropMode(
                page,
                onApply = { vm.setQuad(page.id, it); tool = null },
                onRotate = { vm.rotateClockwise(page.id) },
                onCancel = { tool = null },
            )
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val layout = page.layout
            if (tool == EditorTool.Adjust && layout != null) {
                SheetEditor(
                    layout = layout,
                    pages = pagesById,
                    selectedId = selectedOnSheet,
                    onSelect = { selectedOnSheet = it },
                    onChange = { vm.updatePlacement(page.id, it) },
                )
            } else if (layout != null && tool == null) {
                // Pages on a sheet are shown on their sheet.
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    SheetView(layout, pagesById, Modifier.graphicsLayer { shadowElevation = 16f })
                }
            } else {
                PageCanvas(
                    page = page,
                    brightness = brightness,
                    contrast = contrast,
                    input = when {
                        picking -> CanvasInput.Pick
                        tool == EditorTool.Eraser -> CanvasInput.Draw(StrokeKind.ERASER, eraserColor, eraserWidth)
                        tool == EditorTool.Markup -> CanvasInput.Draw(
                            StrokeKind.PEN,
                            Color(penColor).copy(alpha = penOpacity).toArgb(),
                            penWidth,
                        )
                        else -> CanvasInput.None
                    },
                    onStroke = { vm.addStroke(page.id, it) },
                    onPick = {
                        eraserColor = it
                        picking = false
                    },
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.navigationBarsPadding()) {
                AnimatedContent(
                    targetState = tool,
                    transitionSpec = { fadeIn(DockMotion.standard()) togetherWith fadeOut(DockMotion.standard()) using SizeTransform(clip = false) },
                    label = "toolPanel",
                ) { active ->
                    when (active) {
                        EditorTool.EditText -> InfoPanel(
                            "Editing text needs text recognition (OCR), which is coming in a later version.",
                        )
                        EditorTool.Filters -> FiltersPanel(
                            selected = page.filter,
                            forAll = vm.filterForAll.collectAsStateWithLifecycle().value,
                            onSelect = { vm.setFilter(page.id, it) },
                            onForAll = { vm.setFilterForAll(it, page.id) },
                        )
                        EditorTool.Bright -> BrightPanel(
                            brightness = brightness,
                            contrast = contrast,
                            forAll = vm.adjustForAll.collectAsStateWithLifecycle().value,
                            onBrightness = { brightness = it },
                            onContrast = { contrast = it },
                            onCommit = { vm.setAdjust(page.id, brightness, contrast) },
                            onReset = {
                                brightness = 0f
                                contrast = 0f
                                vm.setAdjust(page.id, 0f, 0f)
                            },
                            onForAll = { vm.setAdjustForAll(it, page.id) },
                        )
                        EditorTool.Eraser -> EraserPanel(
                            color = eraserColor,
                            width = eraserWidth,
                            picking = picking,
                            canUndo = page.strokes.any { it.kind == StrokeKind.ERASER },
                            onPickToggle = { picking = !picking },
                            onWhite = { eraserColor = android.graphics.Color.WHITE; picking = false },
                            onWidth = { eraserWidth = it },
                            onUndo = { vm.undoStroke(page.id, StrokeKind.ERASER) },
                        )
                        EditorTool.Markup -> MarkupPanel(
                            color = penColor,
                            width = penWidth,
                            opacity = penOpacity,
                            canUndo = page.strokes.any { it.kind == StrokeKind.PEN },
                            onColor = { penColor = it },
                            onWidth = { penWidth = it },
                            onOpacity = { penOpacity = it },
                            onUndo = { vm.undoStroke(page.id, StrokeKind.PEN) },
                        )
                        EditorTool.Adjust -> AdjustPanel(
                            vm = vm,
                            page = page,
                            sheets = sheets,
                            selectedId = selectedOnSheet,
                            onSelect = { selectedOnSheet = it },
                        )
                        else -> Spacer(Modifier.height(0.dp))
                    }
                }
                ToolBar(
                    active = tool,
                    onTool = { picked ->
                        picking = false
                        when (picked) {
                            EditorTool.Retake -> onRetake(page.id)
                            EditorTool.Reorder -> onReorder()
                            else -> tool = if (tool == picked) null else picked
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolBar(active: EditorTool?, onTool: (EditorTool) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        items(EditorTool.entries) { t -> ToolButton(t, selected = t == active, onClick = { onTool(t) }) }
    }
}

@Composable
private fun ToolButton(tool: EditorTool, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, DockMotion.press(), label = "toolScale")
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .widthIn(min = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(tool.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(tool.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

@Composable
private fun PanelColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) { content() }
}

@Composable
private fun InfoPanel(text: String) = PanelColumn {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ApplyToAllRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Apply to all pages", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FiltersPanel(selected: PageFilter, forAll: Boolean, onSelect: (PageFilter) -> Unit, onForAll: (Boolean) -> Unit) = PanelColumn {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PageFilter.entries.forEach { f ->
            FilterChip(selected = f == selected, onClick = { onSelect(f) }, label = { Text(f.label) })
        }
    }
    ApplyToAllRow(forAll, onForAll)
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinished, valueRange = range, modifier = Modifier.weight(1f))
        Text(valueText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}

private fun signed(v: Float) = (v * 100).roundToInt().let { if (it > 0) "+$it" else "$it" }

@Composable
private fun BrightPanel(
    brightness: Float,
    contrast: Float,
    forAll: Boolean,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onForAll: (Boolean) -> Unit,
) = PanelColumn {
    LabeledSlider("Brightness", brightness, signed(brightness), -1f..1f, onBrightness, onCommit)
    LabeledSlider("Contrast", contrast, signed(contrast), -1f..1f, onContrast, onCommit)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { ApplyToAllRow(forAll, onForAll) }
        TextButton(onClick = onReset, enabled = brightness != 0f || contrast != 0f) { Text("Reset") }
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit, size: Int = 34) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun EraserPanel(
    color: Int,
    width: Float,
    picking: Boolean,
    canUndo: Boolean,
    onPickToggle: () -> Unit,
    onWhite: () -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit,
) = PanelColumn {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Swatch(color, selected = true, onClick = {})
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Eraser color", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (picking) "Tap the page to pick its color" else "Paints over the page with this color",
                style = MaterialTheme.typography.bodySmall,
                color = if (picking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onWhite) { Text("White") }
        IconButton(onClick = onPickToggle) {
            Icon(
                painterResource(R.drawable.ic_eyedropper),
                contentDescription = "Pick color from page",
                tint = if (picking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            LabeledSlider("Size", width, "${(width * 1000).roundToInt()}", 0.01f..0.12f, onWidth)
        }
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo eraser stroke") }
    }
}

@Composable
private fun MarkupPanel(
    color: Int,
    width: Float,
    opacity: Float,
    canUndo: Boolean,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onUndo: () -> Unit,
) = PanelColumn {
    var customOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PenColors.forEach { c -> Swatch(c, selected = c == color, onClick = { onColor(c) }, size = 32) }
            // Custom color: a hue ring, showing the chosen color in the middle when it isn't a preset.
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
                    .clickable { customOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape)
                        .background(if (color in PenColors) MaterialTheme.colorScheme.surfaceContainer else Color(color)),
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { LabeledSlider("Width", width, "${(width * 1000).roundToInt()}", 0.002f..0.04f, onWidth) }
        IconButton(onClick = onUndo, enabled = canUndo) { Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo pen stroke") }
    }
    LabeledSlider("Opacity", opacity, "${(opacity * 100).roundToInt()}%", 0.1f..1f, onOpacity)
    if (customOpen) {
        ColorPickerDialog(color, onPick = { onColor(it); customOpen = false }, onDismiss = { customOpen = false })
    }
}

@Composable
private fun AdjustPanel(
    vm: ScanViewModel,
    page: Page,
    sheets: List<Page>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) = PanelColumn {
    val layout = page.layout
    var adding by remember { mutableStateOf(false) }
    val hasGuests = layout?.items.orEmpty().size > 1

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = layout == null,
            onClick = { vm.setPaper(page.id, null, false) },
            enabled = !hasGuests,
            label = { Text("Original") },
        )
        Paper.entries.forEach { paper ->
            FilterChip(
                selected = layout?.paper == paper,
                onClick = { vm.setPaper(page.id, paper, layout?.landscape ?: (page.aspect > 1f)) },
                label = { Text(paper.label) },
            )
        }
    }
    if (layout == null) {
        Text(
            "Pick a paper size to place this page on a sheet, resize it, and add other pages to the same sheet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        return@PanelColumn
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !layout.landscape, onClick = { vm.setPaper(page.id, layout.paper, false) }, label = { Text("Portrait") })
        FilterChip(selected = layout.landscape, onClick = { vm.setPaper(page.id, layout.paper, true) }, label = { Text("Landscape") })
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { vm.tidySheet(page.id) }) { Text("Tidy") }
    }
    val selected = selectedId?.let { layout.placementOf(it) }
    if (selected != null) {
        LabeledSlider(
            "Size",
            selected.width,
            "${(selected.width * 100).roundToInt()}%",
            MIN_WIDTH..1f,
            onChange = { vm.updatePlacement(page.id, selected.copy(width = it)) },
        )
    } else {
        Text(
            "Tap a page on the sheet to move or resize it. Pinch to resize.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        FilledTonalButton(onClick = { adding = true }, enabled = sheets.size > 1) {
            Icon(painterResource(R.drawable.ic_add_page), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add page")
        }
        if (selected != null) {
            TextButton(onClick = { vm.updatePlacement(page.id, selected.copy(centerX = 0.5f, centerY = 0.5f)) }) { Text("Center") }
        }
        if (selected != null && selected.pageId != page.id) {
            TextButton(onClick = {
                vm.removeFromSheet(page.id, selected.pageId)
                onSelect(page.id)
            }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (adding) {
        val candidates = sheets.filter { it.id != page.id && it.layout?.items.orEmpty().size <= 1 }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add a page to this sheet") },
            text = {
                if (candidates.isEmpty()) {
                    Text("There are no other pages to add.")
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        candidates.forEach { candidate ->
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        vm.addToSheet(page.id, candidate.id)
                                        onSelect(candidate.id)
                                        adding = false
                                    }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = candidate.output,
                                    contentDescription = null,
                                    modifier = Modifier.size(width = 84.dp, height = 112.dp).background(Color.White),
                                )
                                Text("Page ${sheets.indexOf(candidate) + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CropMode(page: Page, onApply: (Quad) -> Unit, onRotate: () -> Unit, onCancel: () -> Unit) {
    var draft by remember(page.id) { mutableStateOf(page.quad) }
    Column(Modifier.fillMaxSize()) {
        CropEditor(
            source = page.source,
            imageWidth = page.sourceWidth,
            imageHeight = page.sourceHeight,
            quad = draft,
            onQuadChange = { draft = it },
            modifier = Modifier.weight(1f),
        )
        if (!draft.isConvex()) {
            Text(
                "The corners cross over: drag them back into a four-sided shape.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Row {
                    IconButton(onClick = onRotate) {
                        Icon(painterResource(R.drawable.ic_rotate_right), contentDescription = "Rotate page")
                    }
                    TextButton(onClick = { draft = page.detectedQuad ?: Quad.FULL }, enabled = page.detectedQuad != null) {
                        Text("Auto")
                    }
                    TextButton(onClick = { draft = Quad.FULL }) { Text("Full") }
                }
                Button(onClick = { onApply(draft) }, enabled = draft.isConvex()) { Text("Apply") }
            }
        }
    }
}
