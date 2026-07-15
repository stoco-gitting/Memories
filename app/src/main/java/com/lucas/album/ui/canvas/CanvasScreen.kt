package com.lucas.album.ui.canvas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.lucas.album.R
import com.lucas.album.data.local.PhotoLayerEntity

private enum class CanvasMode { Edit, View }

// View mode pans/zooms the whole virtual canvas (CanvasConstants.VIRTUAL_CANVAS_SIZE);
// Edit mode leaves it fixed and drags individual photos instead — the two never run at once.
private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 3f
private const val DEFAULT_ZOOM = 0.4f

// Photos are composed (and their images decoded) only if they fall within this multiple
// of the current on-screen field of view — otherwise hundreds of photos scattered across
// the 4000dp board would all render at once regardless of how few are actually visible.
// >100% so a photo doesn't visibly pop in/out right at the screen edge while panning.
private const val CULL_FOV_MARGIN = 1.3f

private const val CHROME_ENTER_MS = 220
private const val CHROME_EXIT_MS = 130
private const val FAB_ENTER_SCALE = 0.85f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(viewModel: CanvasViewModel, darkMode: Boolean, onToggleDarkMode: () -> Unit) {
    val context = LocalContext.current
    val layers by viewModel.layers.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val photoAddFailed by viewModel.photoAddFailed.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(CanvasMode.Edit) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(DEFAULT_ZOOM) }
    var recenterRequested by remember { mutableStateOf(false) }

    var captionTarget by remember { mutableStateOf<PhotoLayerEntity?>(null) }

    val backupInFlight = backupState == CanvasViewModel.BackupState.Exporting
    val importInFlight = importState == CanvasViewModel.ImportState.Importing

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPhotos(uris) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }

    // The one legitimate use of process-lifecycle observation in this screen: flush any
    // debounced-but-unwritten drag position immediately if she backgrounds mid-gesture.
    // Unrelated to the PIN re-lock decision, which is handled entirely in AppViewModel.
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingSaves()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }

    val exportSuccessMessage = stringResource(R.string.export_success)
    val exportFailureMessage = stringResource(R.string.export_failure)
    LaunchedEffect(exportState) {
        when (exportState) {
            CanvasViewModel.ExportState.Success -> {
                snackbarHostState.showSnackbar(exportSuccessMessage)
                viewModel.resetExportState()
            }
            CanvasViewModel.ExportState.Failure -> {
                snackbarHostState.showSnackbar(exportFailureMessage)
                viewModel.resetExportState()
            }
            else -> Unit
        }
    }

    val photoAddFailureMessage = stringResource(R.string.photo_add_failure)
    LaunchedEffect(photoAddFailed) {
        if (photoAddFailed) {
            snackbarHostState.showSnackbar(photoAddFailureMessage)
            viewModel.resetPhotoAddFailed()
        }
    }

    val backupExportSuccessMessage = stringResource(R.string.backup_export_success)
    val backupExportFailureMessage = stringResource(R.string.backup_export_failure)
    LaunchedEffect(backupState) {
        when (backupState) {
            CanvasViewModel.BackupState.Success -> {
                snackbarHostState.showSnackbar(backupExportSuccessMessage)
                viewModel.resetBackupState()
            }
            CanvasViewModel.BackupState.Failure -> {
                snackbarHostState.showSnackbar(backupExportFailureMessage)
                viewModel.resetBackupState()
            }
            else -> Unit
        }
    }

    val backupImportInvalidFileMessage = stringResource(R.string.backup_import_invalid_file)
    val backupImportUnsupportedVersionMessage = stringResource(R.string.backup_import_unsupported_version)
    val backupImportInsufficientStorageMessage = stringResource(R.string.backup_import_insufficient_storage)
    val backupImportIoErrorMessage = stringResource(R.string.backup_import_io_error)
    LaunchedEffect(importState) {
        when (val state = importState) {
            is CanvasViewModel.ImportState.Success -> {
                val message = if (state.skippedCount > 0) {
                    context.getString(R.string.backup_import_success_with_skipped, state.importedCount, state.skippedCount)
                } else {
                    context.getString(R.string.backup_import_success, state.importedCount)
                }
                snackbarHostState.showSnackbar(message)
                viewModel.resetImportState()
            }
            CanvasViewModel.ImportState.Failure.InvalidFile -> {
                snackbarHostState.showSnackbar(backupImportInvalidFileMessage)
                viewModel.resetImportState()
            }
            CanvasViewModel.ImportState.Failure.UnsupportedVersion -> {
                snackbarHostState.showSnackbar(backupImportUnsupportedVersionMessage)
                viewModel.resetImportState()
            }
            CanvasViewModel.ImportState.Failure.InsufficientStorage -> {
                snackbarHostState.showSnackbar(backupImportInsufficientStorageMessage)
                viewModel.resetImportState()
            }
            CanvasViewModel.ImportState.Failure.IoError -> {
                snackbarHostState.showSnackbar(backupImportIoErrorMessage)
                viewModel.resetImportState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn(tween(CHROME_ENTER_MS))
                        .togetherWith(fadeOut(tween(CHROME_EXIT_MS)))
                        .using(SizeTransform(clip = false))
                },
                label = "canvas-topbar",
            ) { currentMode ->
                if (currentMode == CanvasMode.Edit) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            IconButton(onClick = {
                                overflowExpanded = false
                                mode = CanvasMode.View
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.canvas_mode_edit))
                            }
                            IconButton(onClick = { recenterRequested = true }) {
                                Icon(Icons.Filled.CenterFocusStrong, contentDescription = stringResource(R.string.recenter_canvas))
                            }
                            Box {
                                IconButton(onClick = { overflowExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                                }
                                DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.toggle_theme)) },
                                        leadingIcon = {
                                            Icon(
                                                if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onToggleDarkMode()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.backup_export_action)) },
                                        leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                                        onClick = {
                                            overflowExpanded = false
                                            if (!backupInFlight) {
                                                exportBackupLauncher.launch("memories-backup-${System.currentTimeMillis()}.zip")
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.backup_import_action)) },
                                        leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                                        onClick = {
                                            overflowExpanded = false
                                            if (!importInFlight) {
                                                importBackupLauncher.launch(arrayOf("application/zip"))
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedContent(
                targetState = mode,
                contentAlignment = Alignment.BottomEnd,
                transitionSpec = {
                    (fadeIn(tween(CHROME_ENTER_MS)) + scaleIn(initialScale = FAB_ENTER_SCALE, animationSpec = tween(CHROME_ENTER_MS)))
                        .togetherWith(fadeOut(tween(CHROME_EXIT_MS)))
                        .using(SizeTransform(clip = false))
                },
                label = "canvas-fab",
            ) { currentMode ->
                when (currentMode) {
                    CanvasMode.View -> {
                        FloatingActionButton(onClick = { mode = CanvasMode.Edit }) {
                            Icon(Icons.Filled.PanTool, contentDescription = stringResource(R.string.canvas_mode_view))
                        }
                    }
                    CanvasMode.Edit -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FloatingActionButton(onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.canvas_add_photo))
                            }
                            FloatingActionButton(onClick = { viewModel.export() }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.export_view_gallery))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val virtualSizePx = with(density) { CanvasConstants.VIRTUAL_CANVAS_SIZE.toPx() }

            fun recenter() {
                zoom = DEFAULT_ZOOM
                panX = (viewportWidthPx - virtualSizePx * zoom) / 2f
                panY = (viewportHeightPx - virtualSizePx * zoom) / 2f
            }

            LaunchedEffect(Unit) { recenter() }
            LaunchedEffect(recenterRequested) {
                if (recenterRequested) {
                    recenter()
                    recenterRequested = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mode) {
                        if (mode == CanvasMode.View) {
                            detectTransformGestures { centroid, panDelta, zoomDelta, _ ->
                                val newZoom = (zoom * zoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                panX = centroid.x + (panX - centroid.x) * (newZoom / zoom) + panDelta.x
                                panY = centroid.y + (panY - centroid.y) * (newZoom / zoom) + panDelta.y
                                zoom = newZoom
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .size(CanvasConstants.VIRTUAL_CANVAS_SIZE)
                        .graphicsLayer {
                            translationX = panX
                            translationY = panY
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        },
                ) {
                    // Visible field of view, translated from screen space back into virtual
                    // canvas space (inverting the graphicsLayer transform above), then padded
                    // out to CULL_FOV_MARGIN so photos just past the edge stay rendered.
                    val fovLeft = -panX / zoom
                    val fovTop = -panY / zoom
                    val fovRight = (viewportWidthPx - panX) / zoom
                    val fovBottom = (viewportHeightPx - panY) / zoom
                    val fovCenterX = (fovLeft + fovRight) / 2f
                    val fovCenterY = (fovTop + fovBottom) / 2f
                    val cullHalfWidth = (fovRight - fovLeft) / 2f * CULL_FOV_MARGIN
                    val cullHalfHeight = (fovBottom - fovTop) / 2f * CULL_FOV_MARGIN
                    val cullLeft = fovCenterX - cullHalfWidth
                    val cullRight = fovCenterX + cullHalfWidth
                    val cullTop = fovCenterY - cullHalfHeight
                    val cullBottom = fovCenterY + cullHalfHeight

                    val visibleLayers = layers.filter { layer ->
                        val x = layer.posXFraction * virtualSizePx
                        val y = layer.posYFraction * virtualSizePx
                        x in cullLeft..cullRight && y in cullTop..cullBottom
                    }

                    visibleLayers.sortedBy { it.zIndex }.forEach { layer ->
                        PhotoLayerItem(
                            layer = layer,
                            photoFile = viewModel.fileFor(layer),
                            canvasWidthPx = virtualSizePx,
                            canvasHeightPx = virtualSizePx,
                            editable = mode == CanvasMode.Edit,
                            onTransform = { x, y, s, r -> viewModel.onTransform(layer.id, x, y, s, r) },
                            onGestureStart = { viewModel.bringToFront(layer.id) },
                            onCaptionClick = { captionTarget = layer },
                            onDeleteClick = { viewModel.deleteLayer(layer.id) },
                        )
                    }
                }
            }

            if (layers.isEmpty()) {
                Text(
                    text = stringResource(R.string.canvas_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (backupInFlight || importInFlight) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }

        captionTarget?.let { target ->
            CaptionEditor(
                layer = target,
                onDismiss = { captionTarget = null },
                onSave = { caption ->
                    viewModel.setCaption(target.id, caption)
                    captionTarget = null
                },
            )
        }

        // Renders the real canvas content off-screen (unzoomed, uncropped, every photo) and
        // captures it to a bitmap — export is a snapshot of what's actually on the canvas,
        // never a second hand-drawn approximation of it.
        if (exportState == CanvasViewModel.ExportState.Exporting) {
            CanvasCaptureOverlay(
                layers = layers,
                fileFor = { layer -> viewModel.fileFor(layer) },
                onCaptured = { bitmap -> viewModel.onExportCaptured(bitmap) },
                onFailed = { viewModel.onExportCaptureFailed() },
            )
        }
    }
}
