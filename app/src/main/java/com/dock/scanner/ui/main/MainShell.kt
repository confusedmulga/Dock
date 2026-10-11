package com.dock.scanner.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dock.scanner.R
import com.dock.scanner.data.storage.LibraryItem
import com.dock.scanner.ui.LibraryViewModel
import com.dock.scanner.ui.components.GlassBubble
import com.dock.scanner.ui.components.GlassIsland
import com.dock.scanner.ui.components.ItemDialogs
import com.dock.scanner.ui.components.MainTab
import com.dock.scanner.ui.components.PdfActions
import com.dock.scanner.ui.components.StatusBarBlur
import com.dock.scanner.ui.components.rememberItemDialogState
import com.dock.scanner.ui.components.shareDocument
import com.dock.scanner.ui.home.HomeScreen
import com.dock.scanner.ui.shelf.ShelfScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * The two library tabs with the floating glass island and the scan button. Content scrolls
 * underneath the island, which blurs whatever passes behind it.
 */
@Composable
fun MainShell(
    vm: LibraryViewModel,
    onScan: (folderId: String?) -> Unit,
    onOpenPdf: (LibraryItem) -> Unit,
    onChangeLocation: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    val hazeState = remember { HazeState() }
    val tabStates = rememberSaveableStateHolder()
    val dialogs = rememberItemDialogState()
    val destinations by vm.destinations.collectAsStateWithLifecycle()
    val shelfPath by vm.shelfPath.collectAsStateWithLifecycle()

    // Files may have changed in a file manager while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    LaunchedEffect(tab) { vm.refresh() }

    // Shelf → Home on back, once the Shelf has no folder left to step out of.
    BackHandler(enabled = tab == MainTab.Shelf && shelfPath.isEmpty()) { tab = MainTab.Home }

    val actions = remember(vm) {
        PdfActions(
            onOpen = onOpenPdf,
            onShare = { shareDocument(context, it.uri, it.name) },
            onRename = { dialogs.renaming = it },
            onMove = { dialogs.moving = it },
            onDelete = { dialogs.deleting = it },
        )
    }
    // Island (~58 dp) + its 18 dp gap + a little breathing room, above the gesture bar.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            Crossfade(tab, animationSpec = tween(160), label = "tab") { current ->
                tabStates.SaveableStateProvider(current.name) {
                    when (current) {
                        MainTab.Home -> HomeScreen(vm, actions, bottomInset)
                        MainTab.Shelf -> ShelfScreen(vm, actions, dialogs, bottomInset, onChangeLocation)
                    }
                }
            }
        }

        StatusBarBlur(hazeState, Modifier.align(Alignment.TopCenter))

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIsland(hazeState, selected = tab, onSelect = { tab = it })
            Spacer(Modifier.width(10.dp))
            GlassBubble(
                hazeState,
                painterResource(R.drawable.ic_camera),
                contentDescription = "Scan a document",
                onClick = { onScan(if (tab == MainTab.Shelf) vm.currentFolderId else null) },
            )
        }
    }

    ItemDialogs(dialogs, vm, destinations)
}
