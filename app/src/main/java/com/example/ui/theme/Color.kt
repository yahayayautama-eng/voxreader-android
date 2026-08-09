package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// VoxLeaf: quiet reading surfaces with a single signal-orange playback accent.
val Leaf = Color(0xFF16624A)
val LeafDark = Color(0xFF0A382A)
val LeafContainer = Color(0xFFC4EAD8)
val Ink = Color(0xFF171917)
val Mist = Color(0xFFE4ECE8)
val Canvas = Color(0xFFF7F7F5)
val SignalOrange = Color(0xFFFF6B35)
val SignalOrangeContainer = Color(0xFFFFDBCC)
val Carbon = Color(0xFF111312)
val Graphite = Color(0xFF1C201E)
val NightText = Color(0xFFF2F0EC)

// Placeholder/muted labels sit at 4.5:1 against Carbon; the design handoff's 0.40 alpha measured 3.6:1.
val NightMuted = Color(0xFFBEC7C0)
val TextSecondary = Color(0x9EF2F0EC)
val TextTertiary = Color(0x8AF2F0EC)

// Progress fill and "played" markers. Never used for identity.
val PaleGreen = Color(0xFFC4EAD8)

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

enum class SpineColor(val fill: Color, val onFill: Color) {
    Clay(Color(0xFFAC6047), NightText),
    Slate(Color(0xFF567693), NightText),
    Olive(Color(0xFF8A8853), Carbon),
    Sand(Color(0xFFC9A96B), Carbon),
    Plum(Color(0xFF856885), NightText);

    companion object {
        /** Deterministic per-book: the same book always gets the same spine. */
        fun forKey(key: String): SpineColor = entries[key.hashCode().mod(entries.size)]
    }
}
