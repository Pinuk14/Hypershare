package com.hypershare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassCard(cornerRadius: Dp = 16.dp): Modifier = this
    .background(
        color = Color(0x0DFFFFFF),  // 5% white overlay fill
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        color = Color(0x14FFFFFF),  // 8% white rim border
        shape = RoundedCornerShape(cornerRadius)
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.glassCard(cornerRadius)
    ) {
        content()
    }
}
