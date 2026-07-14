package com.lucas.album.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Fixed, curated palettes — no dynamic/wallpaper-based color — so the app looks the same
// on every device. Dark is the default; light is available as an explicit in-app toggle
// rather than following the system setting.
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

private val DarkColors = darkColorScheme(
    primary = Rose80,
    onPrimary = Rose20,
    primaryContainer = Rose20,
    onPrimaryContainer = Rose80,
    secondary = Plum80,
    onSecondary = Plum20,
    secondaryContainer = Plum20,
    onSecondaryContainer = Plum80,
    background = Ink10,
    onBackground = Cream99,
    surface = Ink10,
    onSurface = Cream99,
    surfaceVariant = Plum20,
    onSurfaceVariant = Plum80,
    outlineVariant = Plum40,
)

@Composable
fun AlbumTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AlbumTypography,
        shapes = AlbumShapes,
        content = content,
    )
}
