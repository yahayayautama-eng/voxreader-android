package com.voxleaf.reader.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxleaf.reader.ui.theme.AzureGlow
import com.voxleaf.reader.ui.theme.AzureGradient
import com.voxleaf.reader.ui.theme.AzureLight
import com.voxleaf.reader.ui.theme.AzurePrimary
import com.voxleaf.reader.ui.theme.SpineColor
import com.voxleaf.reader.ui.theme.TextTertiary

/**
 * Empty states with aesthetic floating stickers (books, mic, audio waves, sparkles).
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showShelf: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (showShelf) {
            AmbientStickerDecorations(alpha = 0.12f)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showShelf) {
                StickerShelfIllustration()
            } else {
                FloatingStickerBadge(
                    icon = icon,
                    color = AzurePrimary,
                    size = 72.dp,
                    iconSize = 36.dp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AzureGradient)
                ) {
                    Button(
                        onClick = onAction,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(actionLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerShelfIllustration() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingStickerBadge(
            icon = Icons.Outlined.AutoStories,
            color = AzurePrimary,
            rotation = -8f,
            size = 68.dp,
            iconSize = 34.dp
        )
        FloatingStickerBadge(
            icon = Icons.Outlined.Mic,
            color = Color(0xFF38BDF8),
            rotation = 0f,
            size = 78.dp,
            iconSize = 40.dp
        )
        FloatingStickerBadge(
            icon = Icons.Outlined.Headphones,
            color = Color(0xFF818CF8),
            rotation = 8f,
            size = 68.dp,
            iconSize = 34.dp
        )
    }
}
