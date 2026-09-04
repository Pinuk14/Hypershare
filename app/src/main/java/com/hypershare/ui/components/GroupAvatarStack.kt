package com.hypershare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.TextPrimary

/**
 * 6-color deterministic palette for group avatars.
 */
private val avatarPalette = listOf(
    Color(0xFF3B82F6), // Signal Blue
    Color(0xFF14B8A6), // Mesh Teal
    Color(0xFFA855F7), // Relay Purple
    Color(0xFFF59E0B), // Warning Amber
    Color(0xFFEF4444), // Error Red
    Color(0xFF22C55E)  // Connected Green
)

/**
 * Deterministic avatar color from userId hash.
 */
fun avatarColorFor(userId: String): Color {
    val index = (userId.hashCode().and(0x7FFFFFFF)) % avatarPalette.size
    return avatarPalette[index]
}

/**
 * Stacked overlapping circular avatars with initials and optional +N overflow badge.
 *
 * @param userIds list of member user IDs
 * @param displayNames map of userId to display name (for initials)
 * @param maxVisible maximum avatars shown before overflow badge
 * @param avatarSize diameter of each circle
 * @param overlapOffset horizontal overlap between circles
 */
@Composable
fun GroupAvatarStack(
    userIds: List<String>,
    displayNames: Map<String, String> = emptyMap(),
    maxVisible: Int = 3,
    avatarSize: Dp = 32.dp,
    overlapOffset: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val visibleIds = userIds.take(maxVisible)
    val overflow = userIds.size - maxVisible

    Box(modifier = modifier) {
        visibleIds.forEachIndexed { index, userId ->
            val color = avatarColorFor(userId)
            val name = displayNames[userId] ?: userId
            val initials = name.take(1).uppercase()

            Box(
                modifier = Modifier
                    .offset(x = overlapOffset * index)
                    .size(avatarSize)
                    .background(color, CircleShape)
                    .border(2.dp, BackgroundBase, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = TextPrimary,
                    fontSize = (avatarSize.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // +N overflow badge
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = overlapOffset * visibleIds.size)
                    .size(avatarSize)
                    .background(Color(0xFF374151), CircleShape)
                    .border(2.dp, BackgroundBase, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflow",
                    color = TextPrimary,
                    fontSize = (avatarSize.value * 0.35f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
