package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paper-dark: a warm, printed-page palette rather than the blue-grey most dark apps default to.
 * Every neutral carries a little yellow, so long reading sessions read as dimmed paper instead of
 * switched-off screen. Brass is the single accent and marks playback only.
 *
 * Contrast against [PaperDark]: ink 14.9:1, secondary 7.2:1, tertiary 4.9:1, brass 7.5:1 — all
 * comfortably past 4.5:1, checked at the smallest size each is used at.
 */
val PaperDark = Color(0xFF14120F)
val PaperSurface = Color(0xFF1E1B16)
val PaperInk = Color(0xFFEDE6D9)
val Brass = Color(0xFFC9A227)
val BrassDim = Color(0xFF6B5514)
val BrassContainer = Color(0xFFF0DFA8)

val PaperLight = Color(0xFFF5F1E8)
val PaperLightSurface = Color(0xFFFFFDF7)
val PaperLightInk = Color(0xFF1C1814)
val PaperLightMuted = Color(0xFFE6DFD1)

// Existing screens address these names directly; re-valuing them here restyles the app without
// touching a single call site.
val Leaf = Brass
val LeafDark = BrassDim
val LeafContainer = BrassContainer
val Ink = PaperLightInk
val Mist = PaperLightMuted
val Canvas = PaperLight
val SignalOrange = Brass
val SignalOrangeContainer = BrassContainer
val Carbon = PaperDark
val Graphite = PaperSurface
val NightText = PaperInk

val NightMuted = Color(0xFFA99F8E)
val TextSecondary = Color(0xFFA99F8E)
val TextTertiary = Color(0xFF8A8172)

// Progress fill and "played" markers. Never used for identity.
val PaleGreen = Color(0xFFD8C98F)

/**
 * Per-book identity colors, deliberately distinct from the functional orange/green so a spine's color
 * never reads as playback state. Each carries its own on-color: white fails 4.5:1 on the lighter
 * spines at the 10sp title size, so sand and olive take ink instead.
 */
/**
 * Marker colors for highlighted passages. Kept low-saturation so a marked sentence stays readable
 * under both reader themes, and distinct from [SpineColor] and the orange playback accent so a
 * highlight never reads as "currently speaking".
 */
enum class HighlightColor(val label: String, val fill: Color) {
    Amber("Amber", Color(0xFFE8B84B)),
    Rose("Rose", Color(0xFFE0748A)),
    Sky("Sky", Color(0xFF63A9D6)),
    Fern("Fern", Color(0xFF6FB07C)),
    Violet("Violet", Color(0xFF9C86C4));

    companion object {
        /** Stored highlights hold an index; an out-of-range one falls back rather than crashing. */
        fun at(index: Int): HighlightColor = entries.getOrElse(index) { entries.first() }
    }
}

/** Book-cloth colours: muted bindings that sit beside brass without competing with it. */
enum class SpineColor(val fill: Color, val onFill: Color) {
    Clay(Color(0xFF9A5138), PaperInk),
    Indigo(Color(0xFF3F4A6B), PaperInk),
    Olive(Color(0xFF6E7248), PaperInk),
    Sand(Color(0xFFBFA073), PaperDark),
    Plum(Color(0xFF6E4A5C), PaperInk);

    companion object {
        /** Deterministic per-book: the same book always gets the same spine. */
        fun forKey(key: String): SpineColor = entries[key.hashCode().mod(entries.size)]
    }
}
