package com.lucas.album.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Fixed palette, no dynamic color and no dark-mode branching — deliberately the same
// curated look on every device, regardless of wallpaper or system theme.
private val LightColors = lightColorScheme(
    primary = Rose40,
    onPrimary = Cream99,
    primaryContainer = Rose80,
    onPrimaryContainer = Rose20,
    secondary = Plum40,
    onSecondary = Cream99,
    secondaryContainer = Plum80,
    onSecondaryContainer = Plum20,
    background = Cream99,
    onBackground = Ink10,
    surface = Cream99,
    onSurface = Ink10,
    surfaceVariant = Rose80,
    onSurfaceVariant = Rose20,
    outlineVariant = Rose80,
)

@Composable
fun AlbumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AlbumTypography,
        shapes = AlbumShapes,
        content = content,
    )
}
