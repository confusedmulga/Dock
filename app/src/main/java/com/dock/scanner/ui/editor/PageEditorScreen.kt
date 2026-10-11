package com.dock.scanner.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dock.scanner.R
import com.dock.scanner.core.PageFilter
import com.dock.scanner.core.Quad
import com.dock.scanner.data.Page
import com.dock.scanner.ui.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(vm: ScanViewModel, pageId: String, onBack: () -> Unit) {
    val pages by vm.pages.collectAsStateWithLifecycle()
    val index = pages.indexOfFirst { it.id == pageId }
    if (index < 0) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val page = pages[index]
    var cropping by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (cropping) "Adjust corners" else "Page ${index + 1} of ${pages.size}") },
                navigationIcon = {
                    IconButton(onClick = { if (cropping) cropping = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!cropping) {
                        IconButton(onClick = {
                            vm.delete(page.id)
                            onBack()
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Delete page") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (cropping) {
                CropMode(page, onApply = { quad ->
                    vm.setQuad(page.id, quad)
                    cropping = false
                }, onCancel = { cropping = false })
            } else {
                ResultMode(
                    page = page,
                    onFilter = { vm.setFilter(page.id, it) },
                    onRotate = { vm.rotateClockwise(page.id) },
                    onCrop = { cropping = true },
                )
            }
        }
    }
}

@Composable
private fun ResultMode(page: Page, onFilter: (PageFilter) -> Unit, onRotate: () -> Unit, onCrop: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (page.output != null) {
                Surface(shadowElevation = 4.dp) {
                    AsyncImage(
                        model = page.output,
                        contentDescription = "Processed page",
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            if (page.processing) CircularProgressIndicator()
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PageFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = page.filter == filter,
                            onClick = { onFilter(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = onCrop) {
                        Icon(painterResource(R.drawable.ic_crop), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Crop")
                    }
                    TextButton(onClick = onRotate) {
                        Icon(painterResource(R.drawable.ic_rotate_right), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rotate")
                    }
                }
            }
        }
    }
}

@Composable
private fun CropMode(page: Page, onApply: (Quad) -> Unit, onCancel: () -> Unit) {
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
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Row {
                    TextButton(onClick = { draft = page.detectedQuad ?: Quad.FULL }, enabled = page.detectedQuad != null) {
                        Text("Auto")
                    }
                    TextButton(onClick = { draft = Quad.FULL }) { Text("Full page") }
                }
                Button(onClick = { onApply(draft) }, enabled = draft.isConvex()) { Text("Apply") }
            }
        }
    }
}
