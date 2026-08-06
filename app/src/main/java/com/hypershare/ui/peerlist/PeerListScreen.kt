package com.hypershare.ui.peerlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerMode
import com.hypershare.ui.components.BottomNavBar
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary

@Composable
fun PeerListScreen(
    viewModel: PeerListViewModel,
    onPeerClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val displayPeers = if (uiState.currentMode == PeerMode.MODE_1_WIFI) {
        listOf(
            ConnectedPeer("peer-1", "USER_NAME (LAN)", status = com.hypershare.model.PeerStatus.CONNECTED, mode = PeerMode.MODE_1_WIFI),
            ConnectedPeer("peer-2", "USER_NAME (LAN)", status = com.hypershare.model.PeerStatus.DISCOVERED, mode = PeerMode.MODE_1_WIFI)
        )
    } else {
        listOf(
            ConnectedPeer("peer-1", "USER_NAME (LAN)", status = com.hypershare.model.PeerStatus.CONNECTED, mode = PeerMode.MODE_1_WIFI),
            ConnectedPeer("peer-2", "USER_NAME (LAN)", status = com.hypershare.model.PeerStatus.DISCOVERED, mode = PeerMode.MODE_1_WIFI),
            ConnectedPeer("peer-3", "USER_NAME (MESH RELAY 2 HOPS)", status = com.hypershare.model.PeerStatus.RELAYING, hopCount = 2, mode = PeerMode.MODE_2_MESH),
            ConnectedPeer("peer-4", "USER_NAME (DISASTER MESH)", status = com.hypershare.model.PeerStatus.CONNECTING, mode = PeerMode.MODE_2_MESH)
        )
    }

    Scaffold(
        containerColor = BackgroundBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomNavBar(
                selectedTab = NavTab.PEERS,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.HOME -> onHomeClick()
                        NavTab.ACCOUNT -> onOpenAccountSettings()
                        NavTab.SETTINGS -> onOpenAppSettings()
                        NavTab.PEERS -> {}
                    }
                },
                onOpenAppSettings = onOpenAppSettings,
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenHome = onHomeClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dedicated Status Bar Spacer Block
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(BackgroundBase)
            )

            // Sleek HYPERSHARE Header Banner with Clickable Home navigation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
                    .background(SignalBlue, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "HYPERSHARE",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier.clickable { onHomeClick() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (uiState.currentMode == PeerMode.MODE_1_WIFI) "● LOCAL MODE (Same Network LAN)" else "▲ EMERGENCY MODE (Local & Multi-Hop Mesh)",
                color = if (uiState.currentMode == PeerMode.MODE_1_WIFI) ConnectedGreen else ErrorRed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayPeers, key = { it.peerId }) { peer ->
                    PeerWireframeListItem(peer = peer, onClick = { onPeerClick(peer.peerId) })
                }
            }
        }
    }
}

@Composable
fun PeerWireframeListItem(
    peer: ConnectedPeer,
    onClick: () -> Unit
) {
    val (statusText, statusColor) = when (peer.status) {
        com.hypershare.model.PeerStatus.CONNECTED -> "__NEW_MESSAGE__" to ConnectedGreen
        com.hypershare.model.PeerStatus.RELAYING -> "__RELAY_MESSAGE__" to SignalBlue
        else -> "__SENT_MESSAGE__" to ErrorRed
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xFF555555), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp)
                    .background(Color.Black, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", color = Color.White, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = peer.displayName,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
