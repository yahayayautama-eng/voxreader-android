package com.voxleaf.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AzurePrimary,
    onPrimary = ObsidianDark,
    primaryContainer = AzureContainer,
    onPrimaryContainer = AzureLight,
    secondary = AzureLight,
    onSecondary = ObsidianDark,
    secondaryContainer = AzureContainer,
    onSecondaryContainer = AzureGlow,
    background = ObsidianDark,
    onBackground = ObsidianInk,
    surface = ObsidianDark,
    onSurface = ObsidianInk,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = ObsidianMuted,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B),
    // Sleek tonal ramp for Obsidian Dark
    surfaceContainerLowest = Color(0xFF04060A),
    surfaceContainerLow = Color(0xFF0A0E17),
    surfaceContainer = Color(0xFF0F172A),
    surfaceContainerHigh = Color(0xFF162035),
    surfaceContainerHighest = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = PaperLightAccent,
    onPrimary = Color.White,
    primaryContainer = PaperLightAccentContainer,
    onPrimaryContainer = PaperLightOnAccentContainer,
    secondary = PaperLightAccent,
    onSecondary = Color.White,
    secondaryContainer = PaperLightAccentContainer,
    onSecondaryContainer = PaperLightOnAccentContainer,
    background = PaperLight,
    onBackground = PaperLightInk,
    surface = PaperLightSurface,
    onSurface = PaperLightInk,
    surfaceVariant = PaperLightMuted,
    onSurfaceVariant = PaperLightSubtle,
    outline = PaperLightOutline,
    outlineVariant = PaperLightContainerHighest,
    // Without these the app falls back to Material's cool default containers and cards stop
    // matching the off-white canvas.
    surfaceContainerLowest = PaperLightContainerLowest,
    surfaceContainerLow = PaperLightContainerLow,
    surfaceContainer = PaperLightContainer,
    surfaceContainerHigh = PaperLightContainerHigh,
    surfaceContainerHighest = PaperLightContainerHighest
)

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
