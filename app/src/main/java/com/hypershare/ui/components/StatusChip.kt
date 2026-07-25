package com.hypershare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.PeerStatus
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.OfflineGray
import com.hypershare.ui.theme.RelayPurple
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.WarningAmber

@Composable
fun StatusChip(
    status: PeerStatus,
    modifier: Modifier = Modifier,
    hopCount: Int = 1
) {
    val (label, bgColor) = when (status) {
        PeerStatus.DISCOVERED -> "DISCOVERED" to OfflineGray
        PeerStatus.CONNECTING -> "CONNECTING..." to WarningAmber
        PeerStatus.CONNECTED -> "CONNECTED" to ConnectedGreen
        PeerStatus.RELAYING -> "RELAY · $hopCount HOP" to RelayPurple
        PeerStatus.LOST -> "LOST" to ErrorRed
    }

    Text(
        text = label,
        color = TextPrimary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
