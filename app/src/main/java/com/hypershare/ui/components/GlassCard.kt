package com.hypershare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hypershare.ui.theme.GlassBorder
import com.hypershare.ui.theme.GlassOverlay

fun Modifier.glassCard(cornerRadius: Dp = 16.dp): Modifier = this
    .background(
        color = GlassOverlay,
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        color = GlassBorder,
        shape = RoundedCornerShape(cornerRadius)
    )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.glassCard(cornerRadius),
        content = content
    )
}
