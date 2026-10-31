package com.max.locked.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Sage, built around #5E8C6A. Light mode sits on a light sage background with lighter cards on
// top; primary (buttons) is a deep sage so it stands out and stays readable.

val SageLight = lightColorScheme(
    primary = Color(0xFF2F5A3C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2D5BA),
    onPrimaryContainer = Color(0xFF0A2614),
    secondary = Color(0xFF506352),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D3),
    onSecondaryContainer = Color(0xFF0E1F12),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F7),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFDAE7DB),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFDAE7DB),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFCAD9CC),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF66756A),
    outlineVariant = Color(0xFFAFC0B2),
    surfaceContainerLowest = Color(0xFFF7FBF6),
    surfaceContainerLow = Color(0xFFEEF5EE),
    surfaceContainer = Color(0xFFE8F0E8),
    surfaceContainerHigh = Color(0xFFF2F7F1),
    surfaceContainerHighest = Color(0xFFE3ECE3),
)

val SageDark = darkColorScheme(
    primary = Color(0xFFA3D0AD),
    onPrimary = Color(0xFF0A3820),
    primaryContainer = Color(0xFF255135),
    onPrimaryContainer = Color(0xFFBFECC8),
    secondary = Color(0xFFB7CCB8),
    onSecondary = Color(0xFF223526),
    secondaryContainer = Color(0xFF384B3B),
    onSecondaryContainer = Color(0xFFD3E8D3),
    tertiary = Color(0xFFA3CDDB),
    onTertiary = Color(0xFF033541),
    tertiaryContainer = Color(0xFF214C58),
    onTertiaryContainer = Color(0xFFBFE9F7),
    background = Color(0xFF101510),
    onBackground = Color(0xFFDFE4DC),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFDFE4DC),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
)
