package com.lucas.album.ui.canvas

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared between PhotoLayerItem and CanvasScreen so the on-screen card size and the
// virtual canvas size are always defined in exactly one place.
object CanvasConstants {
    val BASE_CARD_SIZE: Dp = 140.dp
    val VIRTUAL_CANVAS_SIZE: Dp = 4000.dp
}
