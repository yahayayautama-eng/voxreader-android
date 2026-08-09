package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LeafContainer,
    onPrimary = LeafDark,
    primaryContainer = LeafDark,
    onPrimaryContainer = LeafContainer,
    secondary = SignalOrange,
    onSecondary = Carbon,
    secondaryContainer = Color(0xFF5A2412),
    onSecondaryContainer = SignalOrangeContainer,
    background = Carbon,
    onBackground = NightText,
    surface = Carbon,
    onSurface = NightText,
    surfaceVariant = Graphite,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF89938C),
    // Explicit tonal ramp off Carbon so cards, search, and nav read as distinct layers instead of one flat black.
    surfaceContainerLowest = Color(0xFF0A0B0A),
    surfaceContainerLow = Color(0xFF171917),
    surfaceContainer = Color(0xFF1B1E1C),
    surfaceContainerHigh = Color(0xFF23272A),
    surfaceContainerHighest = Color(0xFF2C3130)
)

private val LightColorScheme = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = LeafContainer,
    onPrimaryContainer = LeafDark,
    secondary = SignalOrange,
    onSecondary = Ink,
    secondaryContainer = SignalOrangeContainer,
    onSecondaryContainer = Color(0xFF4B1B0A),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF42534A),
    outline = Color(0xFF718178)
)

// VoxLeaf ships one bold-contrast dark scheme by default; light stays available for a future toggle.
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = VoxLeafShapes,
        content = content
    )
}
