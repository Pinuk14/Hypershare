package com.hypershare.ui.modetoggle

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.PeerMode
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary

@Composable
fun ModeToggleScreen(
    viewModel: ModeToggleViewModel,
    onDismiss: () -> Unit
) {
    val activeMode by viewModel.selectedMode.collectAsState()

    Scaffold(
        containerColor = BackgroundBase
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Network Mode Switcher",
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            ModeCard(
                title = "Mode 1 — WiFi Local Network",
                description = "Normal Operation. Connects via router / LAN on port 47200.",
                accentColor = SignalBlue,
                isSelected = activeMode == PeerMode.MODE_1_WIFI,
                onClick = { viewModel.selectMode(PeerMode.MODE_1_WIFI) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ModeCard(
                title = "Mode 2 — Disaster Mesh Network",
                description = "⚠ Infrastructure-less mode using WiFi Direct & multi-hop AODV routing.",
                accentColor = MeshTeal,
                isSelected = activeMode == PeerMode.MODE_2_MESH,
                onClick = { viewModel.selectMode(PeerMode.MODE_2_MESH) }
            )
        }
    }
}

@Composable
fun ModeCard(
    title: String,
    description: String,
    accentColor: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, ConnectedGreen, RoundedCornerShape(16.dp))
    } else Modifier

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
