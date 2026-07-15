package com.lucas.album.ui.canvas

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared between on-screen rendering (PhotoLayerItem, CanvasScreen) and export capture
// (CanvasCaptureOverlay) so the two can never drift apart the way they did before —
// export used to assume its own values for these and got them wrong.
object CanvasConstants {
    val BASE_CARD_SIZE: Dp = 140.dp
    val VIRTUAL_CANVAS_SIZE: Dp = 4000.dp
}
