package com.hypershare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.theme.TextPrimary

@Composable
fun QrCodeCard(
    modifier: Modifier = Modifier,
    shareId: String = "HYPERSHARE-ID-8X92"
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(168.dp)) {
                val moduleSize = size.width / 9f

                // Render QR Corner Markers
                fun drawCornerMarker(x: Float, y: Float) {
                    drawRect(Color.Black, Offset(x, y), Size(moduleSize * 3, moduleSize * 3))
                    drawRect(Color.White, Offset(x + moduleSize * 0.5f, y + moduleSize * 0.5f), Size(moduleSize * 2, moduleSize * 2))
                    drawRect(Color.Black, Offset(x + moduleSize, y + moduleSize), Size(moduleSize, moduleSize))
                }

                drawCornerMarker(0f, 0f)
                drawCornerMarker(size.width - moduleSize * 3, 0f)
                drawCornerMarker(0f, size.height - moduleSize * 3)

                // Random center modules simulation
                val pattern = listOf(
                    1 to 4, 2 to 4, 4 to 1, 4 to 2, 4 to 4, 4 to 5, 4 to 7,
                    5 to 4, 6 to 2, 6 to 4, 6 to 6, 7 to 4
                )
                for ((r, c) in pattern) {
                    drawRect(
                        Color.Black,
                        Offset(c * moduleSize, r * moduleSize),
                        Size(moduleSize, moduleSize)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Share ID",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}
