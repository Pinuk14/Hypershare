package com.hypershare.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.RelayPurple
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.WarningAmber

enum class StatusChipState {
    WIFI,
    MESH,
    CONNECTED,
    RELAYING,
    CONNECTING,
    LOST
}

@Composable
fun StatusChip(
    state: StatusChipState,
    hopCount: Int = 1,
    modifier: Modifier = Modifier
) {
    val (label, containerColor) = when (state) {
        StatusChipState.WIFI -> "WIFI" to SignalBlue
        StatusChipState.MESH -> "MESH" to MeshTeal
        StatusChipState.CONNECTED -> "CONNECTED" to ConnectedGreen
        StatusChipState.RELAYING -> "RELAY · $hopCount HOP" to RelayPurple
        StatusChipState.CONNECTING -> "CONNECTING…" to WarningAmber
        StatusChipState.LOST -> "LOST" to ErrorRed
    }

    val pulseScale = if (state == StatusChipState.MESH) {
        val infiniteTransition = rememberInfiniteTransition(label = "mesh_pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        scale
    } else 1.0f

    Box(
        modifier = modifier
            .scale(pulseScale)
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}
