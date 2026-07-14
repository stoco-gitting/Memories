package com.lucas.album.ui.canvas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.lucas.album.R
import com.lucas.album.data.local.PhotoLayerEntity

private enum class CanvasMode { Edit, View }

// The board is much bigger than any screen so hundreds of photos have room to spread out
// without piling on top of each other; View mode pans/zooms this whole space, Edit mode
// leaves it fixed and drags individual photos instead — the two never run at once.
private val VIRTUAL_CANVAS_SIZE = 4000.dp
private const val MIN_ZOOM = 0.1f
private const val MAX_ZOOM = 3f
private const val DEFAULT_ZOOM = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasScreen(viewModel: CanvasViewModel, darkMode: Boolean, onToggleDarkMode: () -> Unit) {
    val layers by viewModel.layers.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val photoAddFailed by viewModel.photoAddFailed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf(CanvasMode.Edit) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var zoom by remember { mutableStateOf(DEFAULT_ZOOM) }
    var recenterRequested by remember { mutableStateOf(false) }

    var captionTarget by remember { mutableStateOf<PhotoLayerEntity?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPhotos(uris) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { mode = if (mode == CanvasMode.Edit) CanvasMode.View else CanvasMode.Edit }) {
                        Icon(
                            if (mode == CanvasMode.Edit) Icons.Filled.Edit else Icons.Filled.PanTool,
                            contentDescription = stringResource(
                                if (mode == CanvasMode.Edit) R.string.canvas_mode_edit else R.string.canvas_mode_view
                            ),
                        )
                    }
                    IconButton(onClick = { recenterRequested = true }) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = stringResource(R.string.recenter_canvas))
                    }
                    IconButton(onClick = onToggleDarkMode) {
                        Icon(
                            if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.toggle_theme),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
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
            val virtualSizePx = with(density) { VIRTUAL_CANVAS_SIZE.toPx() }

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
                        .size(VIRTUAL_CANVAS_SIZE)
                        .graphicsLayer {
                            translationX = panX
                            translationY = panY
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        },
                ) {
                    layers.sortedBy { it.zIndex }.forEach { layer ->
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
    }
}
