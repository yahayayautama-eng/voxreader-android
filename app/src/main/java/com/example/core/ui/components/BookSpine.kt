package com.example.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PaleGreen
import com.example.ui.theme.SpineColor
import com.example.ui.theme.VoxLeafSerif

/**
 * A book rendered as a shelf spine: flat identity color, serif drop-cap, and a progress ribbon pinned
 * to the bottom edge. Extracted cover art wins when a book has it; everything else still reads as
 * designed rather than as a missing asset.
 */
/** Key for the spine -> details cover container transform. Same book, same key, both screens. */
fun bookCoverSharedKey(bookId: String) = "book-cover-$bookId"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookSpine(
    title: String,
    bookId: String,
    modifier: Modifier = Modifier,
    coverPath: String? = null,
    progress: Float = 0f,
    showTitle: Boolean = true,
    initialSize: TextUnit = 26.sp,
    cornerRadius: Int = 8,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null
) {
    val spine = SpineColor.forKey(bookId)
    val shape = RoundedCornerShape(cornerRadius.dp)
    val coverBitmap = rememberBookCoverBitmap(coverPath)
    val sharedModifier = if (sharedScope != null && animatedScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = bookCoverSharedKey(bookId)),
                animatedVisibilityScope = animatedScope,
                clipInOverlayDuringTransition = OverlayClip(shape)
            )
        }
    } else Modifier

    Box(modifier = modifier.then(sharedModifier).clip(shape).background(spine.fill)) {
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Extracted page scans are near-white and glare off a dark shelf; a scrim seats them with the spines.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.10f),
                            0.55f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.42f)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            )
        } else {
            SpineFace(title = title, spine = spine, showTitle = showTitle, initialSize = initialSize)
        }
        ProgressRibbon(progress)
    }
}

@Composable
private fun SpineFace(
    title: String,
    spine: SpineColor,
    showTitle: Boolean,
    initialSize: TextUnit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = if (showTitle) Arrangement.SpaceBetween else Arrangement.Center,
        horizontalAlignment = if (showTitle) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Text(
            text = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            fontFamily = VoxLeafSerif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            fontSize = initialSize,
            color = spine.onFill.copy(alpha = 0.86f),
            maxLines = 1
        )
        if (showTitle) {
            Text(
                text = title.trim().uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                letterSpacing = 0.02.em,
                color = spine.onFill,
                // Font scaling can push a long title past the cell; clamping keeps the spine intact.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BoxScope.ProgressRibbon(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return
    // Progress changes while you listen; growing the ribbon reads as advancement, snapping reads as a glitch.
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "ribbon"
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(animated)
            .height(3.dp)
            .background(PaleGreen)
    )
}

/** Small leading spine used in list rows and the now-listening card, where the title lives beside it. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookSpineThumb(
    title: String,
    bookId: String,
    modifier: Modifier = Modifier,
    coverPath: String? = null,
    progress: Float = 0f
) = BookSpine(
    title = title,
    bookId = bookId,
    modifier = modifier,
    coverPath = coverPath,
    progress = progress,
    showTitle = false,
    initialSize = 24.sp,
    cornerRadius = 6
)
