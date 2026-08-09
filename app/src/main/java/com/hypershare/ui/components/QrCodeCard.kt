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
import androidx.compose.runtime.remember
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
    shareId: String = "HYPERSHARE-ID-8X92",
    payload: String = ""
) {
    val effectivePayload = payload.ifEmpty { shareId }
    val bitMatrix = remember(effectivePayload) {
        QrCodeGenerator.generateBitMatrix(effectivePayload)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitMatrix != null) {
                val matrixSize = bitMatrix.size
                Canvas(modifier = Modifier.size(192.dp)) {
                    val cellSize = size.width / matrixSize.toFloat()

                    for (r in 0 until matrixSize) {
                        for (c in 0 until matrixSize) {
                            if (bitMatrix[r][c]) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(c * cellSize, r * cellSize),
                                    size = Size(cellSize + 0.5f, cellSize + 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "QR Code Generation Error",
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Share Identity QR",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

