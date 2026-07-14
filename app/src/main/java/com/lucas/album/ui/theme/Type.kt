package com.lucas.album.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Placeholder system fonts (Serif/SansSerif) — both have full Portuguese diacritic
// coverage. Swap for bundled custom font files under res/font/ once chosen; verify
// diacritic coverage (á é í ó ú â ê ô ã õ ç) before committing to a replacement.
private val DisplayFontFamily = FontFamily.Serif
private val BodyFontFamily = FontFamily.SansSerif

val AlbumTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)
