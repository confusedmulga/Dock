package com.dock.scanner.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dock.scanner.ui.camera.CameraScreen
import com.dock.scanner.ui.editor.PageEditorScreen
import com.dock.scanner.ui.main.MainShell
import com.dock.scanner.ui.onboarding.StorageSetupScreen
import com.dock.scanner.ui.review.ReviewScreen
import com.dock.scanner.ui.viewer.PdfViewerScreen

private object Routes {
    const val SETUP = "setup"
    const val CHANGE_FOLDER = "change-folder"
    const val MAIN = "main"
    const val CAMERA = "camera"
    const val REVIEW = "review"
    const val PAGE = "page/{id}"
    const val VIEWER = "viewer/{uri}/{name}"
    fun page(id: String) = "page/$id"
    fun viewer(uri: Uri, name: String) = "viewer/${Uri.encode(uri.toString())}/${Uri.encode(name)}"
}

@Composable
fun DockNavHost() {
    val nav = rememberNavController()
    // Created outside the NavHost, so they're scoped to the activity and shared by all screens.
    val scanVm: ScanViewModel = viewModel()
    val libraryVm: LibraryViewModel = viewModel()
    val context = LocalContext.current
    val root by libraryVm.root.collectAsStateWithLifecycle()
    val start = remember { if (libraryVm.root.value == null) Routes.SETUP else Routes.MAIN }

    LaunchedEffect(scanVm) {
        scanVm.events.collect { if (it is UiEvent.Message) Toast.makeText(context, it.text, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(libraryVm) {
        libraryVm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    // Folder access can be revoked from system settings: go back to setup instead of failing silently.
    LaunchedEffect(root) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (root == null && current != null && current != Routes.SETUP) {
            nav.navigate(Routes.SETUP) { popUpTo(0) }
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SETUP) {
            StorageSetupScreen(
                libraryVm,
                isChange = false,
                onDone = { nav.navigate(Routes.MAIN) { popUpTo(Routes.SETUP) { inclusive = true } } },
                onCancel = {},
            )
        }
        composable(Routes.CHANGE_FOLDER) {
            StorageSetupScreen(libraryVm, isChange = true, onDone = { nav.popBackStack() }, onCancel = { nav.popBackStack() })
        }
        composable(Routes.MAIN) {
            MainShell(
                vm = libraryVm,
                onScan = { folderId ->
                    scanVm.targetFolderId = folderId
                    nav.navigate(Routes.CAMERA)
                },
                onOpenPdf = { nav.navigate(Routes.viewer(it.uri, it.displayName)) },
                onChangeLocation = { nav.navigate(Routes.CHANGE_FOLDER) },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(vm = scanVm, onDone = {
                when {
                    // Came from Review to add pages: just go back to it.
                    nav.previousBackStackEntry?.destination?.route == Routes.REVIEW -> nav.popBackStack()
                    scanVm.pages.value.isEmpty() -> nav.popBackStack()
                    else -> nav.navigate(Routes.REVIEW) { popUpTo(Routes.CAMERA) { inclusive = true } }
                }
            }, onImported = {
                if (nav.previousBackStackEntry?.destination?.route == Routes.REVIEW) nav.popBackStack()
                else nav.navigate(Routes.REVIEW) { popUpTo(Routes.CAMERA) { inclusive = true } }
            })
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                scanVm = scanVm,
                libraryVm = libraryVm,
                onAddPages = { nav.navigate(Routes.CAMERA) },
                onOpenPage = { nav.navigate(Routes.page(it)) },
                onFinished = { nav.popBackStack(Routes.MAIN, inclusive = false) },
            )
        }
        composable(Routes.PAGE) { entry ->
            PageEditorScreen(
                vm = scanVm,
                pageId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack(Routes.REVIEW, inclusive = false) },
            )
        }
        composable(Routes.VIEWER) { entry ->
            PdfViewerScreen(
                uri = Uri.parse(entry.arguments?.getString("uri").orEmpty()),
                name = entry.arguments?.getString("name").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
