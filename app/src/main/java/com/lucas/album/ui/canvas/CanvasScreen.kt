package com.lucas.album.ui.canvas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.lucas.album.R
import com.lucas.album.data.local.PhotoLayerEntity

@Composable
fun CanvasScreen(viewModel: CanvasViewModel) {
    val layers by viewModel.layers.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val photoAddFailed by viewModel.photoAddFailed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { maxHeight.toPx() }

            if (layers.isEmpty()) {
                Text(
                    text = stringResource(R.string.canvas_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            layers.sortedBy { it.zIndex }.forEach { layer ->
                PhotoLayerItem(
                    layer = layer,
                    photoFile = viewModel.fileFor(layer),
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onTransform = { x, y, s, r -> viewModel.onTransform(layer.id, x, y, s, r) },
                    onGestureStart = { viewModel.bringToFront(layer.id) },
                    onCaptionClick = { captionTarget = layer },
                    onDeleteClick = { viewModel.deleteLayer(layer.id) },
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
