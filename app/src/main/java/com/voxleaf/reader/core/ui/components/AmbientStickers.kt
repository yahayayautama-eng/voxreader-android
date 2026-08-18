package com.voxleaf.reader.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxleaf.reader.ui.theme.DenimLight
import com.voxleaf.reader.ui.theme.DenimPrimary

/**
 * Large, faint ambient watermark sticker badge for aesthetic app backgrounds.
 */
@Composable
fun FloatingStickerBadge(
    icon: ImageVector,
    color: Color = DenimLight,
    rotation: Float = 0f,
    size: Dp = 120.dp,
    iconSize: Dp = 62.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .rotate(rotation)
            .size(size)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = 0.09f),
                        color.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        color.copy(alpha = 0.18f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.75f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Aesthetic ambient background watermark sticker canvas.
 * Designed to be faint and large, serving as a subtle wallpaper pattern.
 */
@Composable
fun AmbientStickerDecorations(
    modifier: Modifier = Modifier,
    alpha: Float = 0.08f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha)
    ) {
        // Large Floating Open Book (Top Right)
        FloatingStickerBadge(
            icon = Icons.Outlined.AutoStories,
            color = DenimLight,
            rotation = 12f,
            size = 150.dp,
            iconSize = 78.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = 30.dp)
        )

        // Large Floating Studio Microphone (Top Left)
        FloatingStickerBadge(
            icon = Icons.Outlined.Mic,
            color = Color(0xFF60A5FA),
            rotation = -14f,
            size = 135.dp,
            iconSize = 70.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-15).dp, y = 110.dp)
        )

        // Large Floating Audio Wave Equalizer (Center Right)
        FloatingStickerBadge(
            icon = Icons.Outlined.GraphicEq,
            color = Color(0xFF38BDF8),
            rotation = 6f,
            size = 130.dp,
            iconSize = 68.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 24.dp, y = (-20).dp)
        )

        // Large Floating Over-Ear Headphones (Center Left)
        FloatingStickerBadge(
            icon = Icons.Outlined.Headphones,
            color = Color(0xFF818CF8),
            rotation = -18f,
            size = 145.dp,
            iconSize = 76.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-10).dp, y = 80.dp)
        )

        // Large Reading Leaf & Nature Motif (Bottom Left)
        FloatingStickerBadge(
            icon = Icons.Outlined.Spa,
            color = Color(0xFF34D399),
            rotation = 20f,
            size = 115.dp,
            iconSize = 60.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-130).dp)
        )

        // Large Floating Magic Sparkle Star (Bottom Right)
        FloatingStickerBadge(
            icon = Icons.Outlined.Star,
            color = Color(0xFFFBBF24),
            rotation = 16f,
            size = 125.dp,
            iconSize = 64.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 15.dp, y = (-90).dp)
        )
    }
}
