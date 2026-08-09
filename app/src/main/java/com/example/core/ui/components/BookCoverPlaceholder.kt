package com.example.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun coverGradientBrush(color: Color): Brush =
    Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.45f)))

/** Generated cover for books without extracted art: a brand-serif initial over a gradient, so every book reads as designed rather than a blank swatch. */
@Composable
fun BookCoverPlaceholder(title: String, color: Color, modifier: Modifier = Modifier, showTitle: Boolean = false) {
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(coverGradientBrush(color)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = if (showTitle) Modifier.align(Alignment.TopCenter).padding(top = 12.dp) else Modifier
        )
        if (showTitle) {
            Text(
                text = title.trim(),
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}

/** Many imported titles arrive as raw ALL-CAPS filenames; render them in title case without touching stored data. */
fun String.toDisplayTitle(): String {
    if (this != uppercase()) return this
    return lowercase().split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}
