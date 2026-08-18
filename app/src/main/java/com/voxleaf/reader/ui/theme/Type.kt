package com.voxleaf.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.voxleaf.reader.R

/**
 * Three families, each with one job — the split a printed book already makes between running text,
 * chapter openers, and the small caps on a copyright page.
 *
 * Every file is a variable font, so a weight resolves to its own instance instead of shipping one
 * file per weight.
 */

/** Running text and most UI. A text serif, drawn for paragraphs at reading sizes. */
@OptIn(ExperimentalTextApi::class)
val VoxLeafFont = FontFamily(
    Font(R.font.source_serif_variable, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.source_serif_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.source_serif_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.source_serif_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.source_serif_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Titles and headings. Higher contrast than the body serif, so a heading reads as a heading. */
@OptIn(ExperimentalTextApi::class)
val VoxLeafDisplay = FontFamily(
    Font(R.font.newsreader_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.newsreader_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.newsreader_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.newsreader_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** The wordmark and a single hero line per screen. Italic is the point — never use it for body. */
val VoxLeafSerif = FontFamily(
    Font(R.font.newsreader_italic, FontWeight.SemiBold, FontStyle.Italic)
)

/**
 * Section labels, counts, timers. Monospace keeps a running timer from reflowing digit by digit,
 * and its evenness is what makes small uppercase labels read as labels rather than shouted text.
 */
@OptIn(ExperimentalTextApi::class)
val VoxLeafMono = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

private val body = TextStyle(fontFamily = VoxLeafFont)
private val display = TextStyle(fontFamily = VoxLeafDisplay)
private val label = TextStyle(fontFamily = VoxLeafMono)

val Typography = Typography(
    // Tightened tracking as size grows: a serif set large looks loose at default spacing.
    displayLarge = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 57.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp),
    displayMedium = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 45.sp, lineHeight = 50.sp, letterSpacing = (-1.0).sp),
    displaySmall = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),
    headlineLarge = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = display.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = display.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = display.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    // 1.6 line-height on body: paragraphs of serif need more leading than a UI sans does.
    bodyLarge = body.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = body.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = body.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    // Material routes button, chip, and navigation text through the label roles, so these stay in the
    // reading serif — monospaced nav labels read as a terminal, not a library. Mono is applied
    // deliberately via [Eyebrow] where a tracked caps label is actually wanted.
    labelLarge = body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = body.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = body.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)

/**
 * Small tracked caps for section headers, straplines and timers — the running-head voice. Kept as an
 * explicit style rather than a Material role so it never leaks into buttons or navigation.
 */
val Eyebrow = TextStyle(
    fontFamily = VoxLeafMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.6.sp
)
