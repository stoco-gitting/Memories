package com.lucas.album.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lucas.album.data.local.PhotoLayerEntity
import java.io.File

@Composable
fun PhotoLayerItem(
    layer: PhotoLayerEntity,
    photoFile: File,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    editable: Boolean,
    onTransform: (posXFraction: Float, posYFraction: Float, scale: Float, rotationDegrees: Float) -> Unit,
    onGestureStart: () -> Unit,
    onCaptionClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val baseSizeDp = 140.dp

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = layer.posXFraction * canvasWidthPx - size.width / 2f
                translationY = layer.posYFraction * canvasHeightPx - size.height / 2f
                scaleX = layer.scale
                scaleY = layer.scale
                rotationZ = layer.rotationDegrees
            }
            .size(baseSizeDp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(8.dp), clip = false)
            .background(Color.White, RoundedCornerShape(8.dp))
            .then(
                if (editable) {
                    // Local accumulators seeded once when this gesture-detector coroutine
                    // starts, then mutated only locally — reading `layer.*` on every callback
                    // instead would use a stale snapshot from whenever this block launched,
                    // since recomposition doesn't restart pointerInput for the same key.
                    Modifier.pointerInput(layer.id) {
                        var currentX = layer.posXFraction
                        var currentY = layer.posYFraction
                        var currentScale = layer.scale
                        var currentRotation = layer.rotationDegrees

                        detectTransformGestures { _, pan, zoom, rotation ->
                            onGestureStart()
                            currentX = ((currentX * canvasWidthPx + pan.x) / canvasWidthPx).coerceIn(0f, 1f)
                            currentY = ((currentY * canvasHeightPx + pan.y) / canvasHeightPx).coerceIn(0f, 1f)
                            currentScale = (currentScale * zoom).coerceIn(0.3f, 4f)
                            currentRotation += rotation
                            onTransform(currentX, currentY, currentScale, currentRotation)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            AsyncImage(
                model = photoFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(baseSizeDp - 12.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            if (!layer.caption.isNullOrBlank()) {
                Text(
                    text = layer.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onCaptionClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
