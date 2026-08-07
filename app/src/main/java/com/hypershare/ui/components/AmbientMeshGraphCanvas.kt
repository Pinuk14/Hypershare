package com.hypershare.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.SignalBlue
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientMeshGraphCanvas(
    modifier: Modifier = Modifier,
    isDisasterMode: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_drift")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val lineColor = if (isDisasterMode) MeshTeal.copy(alpha = 0.12f) else SignalBlue.copy(alpha = 0.10f)
    val dotColor = if (isDisasterMode) MeshTeal.copy(alpha = 0.25f) else SignalBlue.copy(alpha = 0.20f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Define 6 ambient topology nodes with subtle drift offset
        val nodes = listOf(
            Offset(w * 0.2f + 15f * cos(phase), h * 0.25f + 10f * sin(phase)),
            Offset(w * 0.75f + 12f * sin(phase * 1.2f), h * 0.2f + 15f * cos(phase)),
            Offset(w * 0.5f + 18f * cos(phase * 0.8f), h * 0.45f + 12f * sin(phase * 0.8f)),
            Offset(w * 0.25f + 10f * sin(phase * 1.5f), h * 0.7f + 14f * cos(phase * 1.5f)),
            Offset(w * 0.8f + 14f * cos(phase * 1.1f), h * 0.65f + 10f * sin(phase * 1.1f)),
            Offset(w * 0.45f + 16f * sin(phase * 0.9f), h * 0.85f + 12f * cos(phase * 0.9f))
        )

        // Draw ambient mesh topology links between nearby nodes
        val connections = listOf(
            0 to 1, 0 to 2, 1 to 2, 2 to 3, 2 to 4, 3 to 5, 4 to 5
        )

        for ((start, end) in connections) {
            drawLine(
                color = lineColor,
                start = nodes[start],
                end = nodes[end],
                strokeWidth = 2f
            )
        }

        // Draw ambient node points
        for (node in nodes) {
            drawCircle(
                color = dotColor,
                radius = 5f,
                center = node
            )
        }
    }
}
