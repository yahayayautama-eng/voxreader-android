package com.voxleaf.reader.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Luminous Sky & Cobalt Palette (Option 1).
 *
 * Primary accent is luminous sky cyan (#38BDF8) blending into rich cobalt blue (#2563EB)
 * with radiant sky glow (#7DD3FC) highlights for high contrast, vibrant play/pause controls,
 * and eye-comfort highlights.
 * Neutrals are deep slate midnight (#090D16, #0F172A, #1E293B).
 */
val SkyPrimary = Color(0xFF38BDF8)
val SkyCobalt = Color(0xFF2563EB)
val SkyGlow = Color(0xFF7DD3FC)
val SkyLight = Color(0xFFBAE6FD)
val SkyContainer = Color(0xFF0F2645)
val SkyBorder = Color(0xFF0284C7)

// Aliases mapped to Luminous Sky & Cobalt
val AzurePrimary = SkyPrimary
val AzureLight = SkyGlow
val AzureDim = SkyCobalt
val AzureContainer = SkyContainer
val AzureGlow = SkyGlow
val AzureBorder = SkyBorder
val DenimPrimary = SkyPrimary
val DenimLight = SkyGlow
val DenimDim = SkyCobalt

val ObsidianDark = Color(0xFF090D16)
val ObsidianSurface = Color(0xFF0F172A)
val ObsidianCard = Color(0xFF1E293B)
val ObsidianCardHigh = Color(0xFF334155)
val ObsidianBorder = Color(0xFF334155)
val ObsidianInk = Color(0xFFF3F4F6)
val ObsidianMuted = Color(0xFF9CA3AF)
// Tertiary copy is used on the dark canvas; #9CA3AF keeps normal-sized labels above 4.5:1.
val ObsidianSubtle = Color(0xFF9CA3AF)

val AzureGradient = Brush.horizontalGradient(
    listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
)
val AzureVerticalGradient = Brush.verticalGradient(
    listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
)

// Legacy aliases remapped to Azure & Obsidian
val Brass = AzurePrimary
val BrassDim = AzureDim
val BrassContainer = AzureContainer
val PaperDark = ObsidianDark
val PaperSurface = ObsidianSurface
val PaperInk = ObsidianInk

/**
 * Warm off-white light theme.
 *
 * Deliberately no pure white anywhere: #FFFFFF next to a warm ink reads as a browser page and is
 * harsh under a reading lamp, which is the wrong feel for an app people stare at for hours. The
 * ramp is warm-neutral (a touch of yellow, no blue) so it reads as paper rather than as a screen,
 * and every step stays inside the off-white family so raised surfaces lift without going white.
 */
val PaperLight = Color(0xFFF7F4EE)
val PaperLightSurface = Color(0xFFFCFAF6)
val PaperLightInk = Color(0xFF1C1917)
val PaperLightMuted = Color(0xFFEBE6DC)

// Tonal ramp for light mode. Material's default light containers are cool/lavender-tinted, so
// these must be supplied explicitly or cards drift away from the off-white canvas.
val PaperLightContainerLowest = Color(0xFFFFFEFB)
val PaperLightContainerLow = Color(0xFFFCFAF6)
val PaperLightContainer = Color(0xFFF2EEE6)
val PaperLightContainerHigh = Color(0xFFEBE6DC)
val PaperLightContainerHighest = Color(0xFFE4DED2)

/** Secondary copy on the off-white canvas; #57534E holds ~7:1, well clear of the 4.5:1 floor. */
val PaperLightSubtle = Color(0xFF57534E)
val PaperLightOutline = Color(0xFFD6CFC2)

/**
 * The dark-mode accent (#38BDF8) is far too light to sit on off-white — roughly 1.9:1, failing
 * both text and non-text contrast. Light mode therefore uses a deeper tone of the same hue family.
 * This is the single token to change if the accent family is ever revisited.
 */
val PaperLightAccent = Color(0xFF0369A1)
val PaperLightAccentContainer = Color(0xFFDCEEFA)
val PaperLightOnAccentContainer = Color(0xFF02456B)

val Leaf = AzurePrimary
val LeafDark = AzureDim
val LeafContainer = AzureContainer
val Ink = PaperLightInk
val Mist = PaperLightMuted
val Canvas = ObsidianDark
val SignalOrange = AzurePrimary
val SignalOrangeContainer = AzureContainer
val Carbon = ObsidianDark
val Graphite = ObsidianSurface
val NightText = ObsidianInk

val NightMuted = ObsidianMuted
val TextSecondary = ObsidianMuted
val TextTertiary = ObsidianSubtle

val PaleGreen = AzureLight

/**
 * Marker colors for highlighted passages. Low-saturation and readable against Obsidian dark.
 */
enum class HighlightColor(val label: String, val fill: Color) {
    Azure("Azure", Color(0xFF38BDF8)),
    Amber("Amber", Color(0xFFFBBF24)),
    Rose("Rose", Color(0xFFF43F5E)),
    Emerald("Emerald", Color(0xFF10B981)),
    Violet("Violet", Color(0xFFA855F7));

    companion object {
        fun at(index: Int): HighlightColor = entries.getOrElse(index) { entries.first() }
    }
}

/** Book spine colors tuned for the Obsidian Midnight theme. */
enum class SpineColor(val fill: Color, val onFill: Color) {
    Azure(Color(0xFF0284C7), ObsidianInk),
    Indigo(Color(0xFF4F46E5), ObsidianInk),
    Teal(Color(0xFF0D9488), ObsidianInk),
    Violet(Color(0xFF7C3AED), ObsidianInk),
    Rose(Color(0xFFE11D48), ObsidianInk);

    companion object {
        fun forKey(key: String): SpineColor = entries[key.hashCode().mod(entries.size)]
    }
}
