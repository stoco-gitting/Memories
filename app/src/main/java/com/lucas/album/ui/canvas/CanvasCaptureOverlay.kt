package com.lucas.album.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lucas.album.data.local.PhotoLayerEntity
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

// Parked far outside any real viewport so it never visibly flashes on screen while
// composing/capturing.
private val OFFSCREEN_OFFSET = 100_000.dp

private data class CanvasBounds(val minXDp: Float, val minYDp: Float, val widthDp: Float, val heightDp: Float)

// Bounding box of every photo (position + rotated footprint), independent of the user's
// current pan/zoom/viewport — this is what makes export correct regardless of how many
// photos there are or where she's scrolled to.
private fun computeCanvasBounds(layers: List<PhotoLayerEntity>): CanvasBounds? {
    if (layers.isEmpty()) return null
    val virtualSizeDp = CanvasConstants.VIRTUAL_CANVAS_SIZE.value
    // Half-diagonal, not half-width: a conservative radius so a rotated card's corners are
    // never clipped, at the cost of a little extra margin in the common unrotated case.
    val cardHalfDiagonalDp = (CanvasConstants.BASE_CARD_SIZE.value / 2f) * sqrt(2f)

    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    layers.forEach { layer ->
        val cx = layer.posXFraction * virtualSizeDp
        val cy = layer.posYFraction * virtualSizeDp
        val r = cardHalfDiagonalDp * layer.scale
        minX = minOf(minX, cx - r)
        maxX = maxOf(maxX, cx + r)
        minY = minOf(minY, cy - r)
        maxY = maxOf(maxY, cy + r)
    }

    val marginDp = CanvasConstants.BASE_CARD_SIZE.value * 0.3f
    minX -= marginDp
    minY -= marginDp
    maxX += marginDp
    maxY += marginDp

    return CanvasBounds(minXDp = minX, minYDp = minY, widthDp = maxX - minX, heightDp = maxY - minY)
}

// Renders the real PhotoLayerItem composables — unmodified, same frame/crop/caption as
// on-screen — off-screen and un-zoomed, then captures that to a bitmap. This is what
// guarantees export matches the canvas: it IS the canvas, not a redrawn approximation of it.
@Composable
fun CanvasCaptureOverlay(
    layers: List<PhotoLayerEntity>,
    fileFor: (PhotoLayerEntity) -> File,
    onCaptured: (Bitmap) -> Unit,
    onFailed: () -> Unit,
) {
    val bounds = remember(layers) { computeCanvasBounds(layers) }
    if (bounds == null) {
        LaunchedEffect(Unit) { onFailed() }
        return
    }

    val graphicsLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val virtualSizePx = with(density) { CanvasConstants.VIRTUAL_CANVAS_SIZE.toPx() }

    Box(modifier = Modifier.offset(x = OFFSCREEN_OFFSET, y = OFFSCREEN_OFFSET)) {
        Box(
            modifier = Modifier
                .size(bounds.widthDp.dp, bounds.heightDp.dp)
                .clipToBounds()
                .background(Color(0xFFFFFBFA))
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
        ) {
            Box(
                modifier = Modifier
                    .size(CanvasConstants.VIRTUAL_CANVAS_SIZE)
                    .offset(x = (-bounds.minXDp).dp, y = (-bounds.minYDp).dp),
            ) {
                layers.sortedBy { it.zIndex }.forEach { layer ->
                    PhotoLayerItem(
                        layer = layer,
                        photoFile = fileFor(layer),
                        canvasWidthPx = virtualSizePx,
                        canvasHeightPx = virtualSizePx,
                        editable = false,
                        onTransform = { _, _, _, _ -> },
                        onGestureStart = {},
                        onCaptionClick = {},
                        onDeleteClick = {},
                    )
                }
            }
        }

        LaunchedEffect(bounds) {
            // Wait a couple of frames to be sure the content above has actually drawn (and
            // thus been recorded into the layer) before reading it back.
            withFrameNanos {}
            withFrameNanos {}
            try {
                val imageBitmap = graphicsLayer.toImageBitmap()
                onCaptured(imageBitmap.asAndroidBitmap())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onFailed()
            }
        }
    }
}
