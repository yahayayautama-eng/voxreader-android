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
