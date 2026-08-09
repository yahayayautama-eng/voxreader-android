package com.example.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LoadingState(modifier: Modifier = Modifier, contentDescription: String = "Loading") {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                this.contentDescription = contentDescription
            }
        )
    }
}

/**
 * Shelf-shaped skeleton: the library loads into the layout it's about to show rather than parking a
 * spinner in the middle of the screen, so nothing jumps when the real spines arrive.
 */
@Composable
fun ShelfSkeleton(
    columns: Int,
    modifier: Modifier = Modifier,
    contentDescription: String = "Loading your library"
) {
    val pulse = rememberPulseAlpha()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .semantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(30.dp), pulse, 8)
        SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), pulse, 26)
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(columns) {
                    SkeletonBlock(Modifier.weight(1f).aspectRatio(2f / 3f), pulse, 8)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, alpha: Float, cornerRadius: Int) {
    Box(
        modifier = modifier
            .clip(if (cornerRadius >= 26) CircleShape else RoundedCornerShape(cornerRadius.dp))
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@Composable
private fun rememberPulseAlpha(): Float {
    // Headless renderers and previews never advance the clock; a static value keeps the skeleton visible.
    if (LocalInspectionMode.current) return 0.7f
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha"
    ).value
}
