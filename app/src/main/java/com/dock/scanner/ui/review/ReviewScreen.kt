package com.dock.scanner.ui.review

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dock.scanner.R
import com.dock.scanner.data.Page
import com.dock.scanner.ui.Destination
import com.dock.scanner.ui.LibraryViewModel
import com.dock.scanner.ui.ScanViewModel
import com.dock.scanner.ui.components.EmptyMessage
import com.dock.scanner.ui.components.GlassBubble
import com.dock.scanner.ui.components.LargeTitle
import com.dock.scanner.ui.components.StatusBarBlur
import com.dock.scanner.ui.theme.DockMotion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** The scan in progress: check pages, add more, then save it as a PDF to the library. */
@Composable
fun ReviewScreen(
    scanVm: ScanViewModel,
    libraryVm: LibraryViewModel,
    onAddPages: () -> Unit,
    onOpenPage: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val pages by scanVm.pages.collectAsStateWithLifecycle()
    val saving by scanVm.exporting.collectAsStateWithLifecycle()
    val destinations by libraryVm.destinations.collectAsStateWithLifecycle()
    val root by libraryVm.root.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var saveSheet by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scanVm.importImages(uris)
    }
    fun leave() = if (pages.isEmpty()) onFinished() else confirmDiscard = true
    BackHandler { leave() }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top, bottom = bottom),
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // The grid is inset 14 dp; 6 dp more puts text on the same 20 dp line as other screens.
                Column {
                    TextButton(onClick = ::leave, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Cancel") }
                    LargeTitle(
                        "New scan",
                        subtitle = if (pages.isEmpty()) null else "${pages.size} page${if (pages.size == 1) "" else "s"} · tap a page to crop or change its look",
                        start = 6.dp,
                    )
                }
            }
            if (pages.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyMessage("No pages", "Add pages with the camera or from your photos.")
                }
            }
            itemsIndexed(pages, key = { _, p -> p.id }) { i, page ->
                PageCard(i + 1, page, onClick = { onOpenPage(page.id) })
            }
        }

        StatusBarBlur(hazeState, Modifier.align(Alignment.TopCenter))

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBubble(
                hazeState,
                painterResource(R.drawable.ic_gallery),
                contentDescription = "Add pages from photos",
                onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            Spacer(Modifier.width(10.dp))
            GlassBubble(hazeState, painterResource(R.drawable.ic_camera), contentDescription = "Add pages with the camera", onClick = onAddPages)
            Spacer(Modifier.width(10.dp))
            SaveCapsule(enabled = pages.isNotEmpty() && !saving, busy = saving, onClick = { saveSheet = true })
        }
    }

    if (saveSheet && root != null) {
        SaveSheet(
            suggestedName = remember { scanVm.suggestedFileName() },
            destinations = destinations,
            preselected = scanVm.targetFolderId,
            onDismiss = { saveSheet = false },
            onSave = { name, destination ->
                saveSheet = false
                scanVm.saveToLibrary(libraryVm.library, root!!, destination.documentId, name) {
                    android.widget.Toast.makeText(context, "Saved to ${destination.name}", android.widget.Toast.LENGTH_SHORT).show()
                    libraryVm.refresh()
                    onFinished()
                }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this scan?") },
            text = { Text("${if (pages.size == 1) "The page" else "The ${pages.size} pages"} you scanned will be deleted. Nothing has been saved yet.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    scanVm.clear()
                    onFinished()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

/** The one solid, colored control: the primary action stands apart from the glass chrome. */
@Composable
private fun SaveCapsule(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, DockMotion.press(), label = "saveScale")
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 10.dp,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (busy) "Saving…" else "Save PDF", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(
    suggestedName: String,
    destinations: List<Destination>,
    preselected: String?,
    onDismiss: () -> Unit,
    onSave: (String, Destination) -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    var target by remember(destinations) {
        mutableStateOf(destinations.firstOrNull { it.documentId == preselected } ?: destinations.firstOrNull())
    }
    val valid = name.isNotBlank() && name.none { it in "/\\:*?\"<>|" } && target != null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Save PDF", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                suffix = { Text(".pdf") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text("Save to", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                destinations.forEachIndexed { i, destination ->
                    FilterChip(
                        selected = destination == target,
                        onClick = { target = destination },
                        label = { Text(if (i == 0) "${destination.name} (main)" else destination.name) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(name.trim(), target!!) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun PageCard(number: Int, page: Page, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = "Edit page $number", onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (page.output != null) {
            AsyncImage(
                model = page.output,
                contentDescription = "Page $number",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (page.processing) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp))
        }
    }
}
