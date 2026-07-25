package com.hypershare.ui.peerlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerMode
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.StatusChip
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary

@Composable
fun PeerListScreen(
    viewModel: PeerListViewModel,
    onPeerClick: (String) -> Unit,
    onOpenModeToggle: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BackgroundBase,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.toggleMode()
                    onOpenModeToggle()
                },
                containerColor = if (uiState.currentMode == PeerMode.MODE_1_WIFI) SignalBlue else MeshTeal
            ) {
                Text(
                    text = if (uiState.currentMode == PeerMode.MODE_1_WIFI) "WIFI" else "MESH",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "HyperShare Peers",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Active Mode: ${if (uiState.currentMode == PeerMode.MODE_1_WIFI) "Mode 1 (WiFi LAN)" else "Mode 2 (Disaster Mesh)"}",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (uiState.peers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Scanning for nearby peers...",
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.peers, key = { it.peerId }) { peer ->
                        PeerListItem(peer = peer, onClick = { onPeerClick(peer.peerId) })
                    }
                }
            }
        }
    }
}

@Composable
fun PeerListItem(
    peer: ConnectedPeer,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .background(SignalBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.displayName.take(1).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = peer.ipAddress?.hostAddress ?: "Hop count: ${peer.hopCount}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            StatusChip(status = peer.status, hopCount = peer.hopCount)
        }
    }
}
