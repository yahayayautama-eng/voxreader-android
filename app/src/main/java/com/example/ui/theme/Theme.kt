package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Brass,
    onPrimary = PaperDark,
    primaryContainer = BrassDim,
    onPrimaryContainer = BrassContainer,
    secondary = Brass,
    onSecondary = PaperDark,
    secondaryContainer = BrassDim,
    onSecondaryContainer = BrassContainer,
    background = PaperDark,
    onBackground = PaperInk,
    surface = PaperDark,
    onSurface = PaperInk,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF6F675A),
    outlineVariant = Color(0xFF3A342B),
    // Warm tonal ramp off PaperDark so cards, search and nav read as stacked paper rather than one
    // flat field. Each step keeps the same yellow bias — a neutral grey here would read as a hole.
    surfaceContainerLowest = Color(0xFF0F0D0B),
    surfaceContainerLow = Color(0xFF1A1712),
    surfaceContainer = Color(0xFF1E1B16),
    surfaceContainerHigh = Color(0xFF262219),
    surfaceContainerHighest = Color(0xFF302B21)
)

private val LightColorScheme = lightColorScheme(
    primary = BrassDim,
    onPrimary = Color.White,
    primaryContainer = BrassContainer,
    onPrimaryContainer = Color(0xFF3A2E06),
    secondary = BrassDim,
    onSecondary = Color.White,
    secondaryContainer = BrassContainer,
    onSecondaryContainer = Color(0xFF3A2E06),
    background = PaperLight,
    onBackground = PaperLightInk,
    surface = PaperLightSurface,
    onSurface = PaperLightInk,
    surfaceVariant = PaperLightMuted,
    onSurfaceVariant = Color(0xFF564E42),
    outline = Color(0xFF847A6C)
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
