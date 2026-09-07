package com.hypershare.ui.peerlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.wifi.p2p.WifiP2pDevice
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerMode
import com.hypershare.model.PeerStatus
import com.hypershare.ui.components.AmbientMeshGraphCanvas
import com.hypershare.ui.components.BottomNavBar
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.components.StatusChip
import com.hypershare.ui.components.StatusChipState
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.RelayPurple
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary
import com.hypershare.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerListScreen(
    viewModel: PeerListViewModel,
    onPeerClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onRefreshDiscovery: (() -> Unit)? = null,
    onWifiDirectPeerClick: (WifiP2pDevice) -> Unit = {},
    onRefreshWifiDirectDiscovery: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val isWifiDirectMode = uiState.currentMode == PeerMode.MODE_2_MESH
    val wifiDirectPeersById = remember(uiState.wifiDirectPeers, uiState.wifiDirectPeerIds) {
        uiState.wifiDirectPeers.associateBy { device ->
            uiState.wifiDirectPeerIds[device.deviceAddress] ?: device.deviceAddress
        }
    }

    // P2P mode deliberately shows only real Wi-Fi Direct devices, never demo peers.
    val displayPeers = if (isWifiDirectMode) {
        uiState.wifiDirectPeers.map { device ->
            ConnectedPeer(
                peerId = uiState.wifiDirectPeerIds[device.deviceAddress] ?: device.deviceAddress,
                displayName = device.deviceName.ifBlank { device.deviceAddress },
                status = when {
                    uiState.wifiDirectConnection.isConnected -> PeerStatus.CONNECTED
                    uiState.wifiDirectConnection.isConnecting -> PeerStatus.CONNECTING
                    else -> PeerStatus.DISCOVERED
                },
                mode = PeerMode.MODE_2_MESH
            )
        }
    } else if (uiState.peers.isNotEmpty()) {
        uiState.peers
    } else if (uiState.showDemoPeers) {
            listOf(
                ConnectedPeer("peer-1", "Device Alpha (LAN)", status = PeerStatus.CONNECTED, mode = PeerMode.MODE_1_WIFI),
                ConnectedPeer("peer-2", "Device Beta (LAN)", status = PeerStatus.DISCOVERED, mode = PeerMode.MODE_1_WIFI)
            )
    } else {
        emptyList()
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ambient Live Mesh Topology Graph Background
            AmbientMeshGraphCanvas(isDisasterMode = uiState.currentMode == PeerMode.MODE_2_MESH)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Bar Spacer Block
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(BackgroundBase)
                )

                // Glassmorphic Header Banner with Refresh Button
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    cornerRadius = 16.dp
                ) {
                    val totalUnreadCount = remember(context) {
                        com.hypershare.db.MessageRepository(context).getTotalUnreadMessageCount()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "HYPERSHARE",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                modifier = Modifier.clickable { onHomeClick() }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            StatusChip(
                                state = if (uiState.currentMode == PeerMode.MODE_1_WIFI) StatusChipState.WIFI else StatusChipState.MESH
                            )
                            if (totalUnreadCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(SignalBlue, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$totalUnreadCount unread",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Top Bar Refresh Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0x22FFFFFF), CircleShape)
                                .clickable {
                                    viewModel.refreshPeers(
                                        if (isWifiDirectMode) onRefreshWifiDirectDiscovery else onRefreshDiscovery
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isRefreshing) "⏳" else "↻",
                                color = SignalBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (!isWifiDirectMode) "● LOCAL NETWORK (mDNS LAN)" else "▲ WI-FI DIRECT PEER DISCOVERY",
                    color = if (!isWifiDirectMode) ConnectedGreen else ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isWifiDirectMode) {
                    val connection = uiState.wifiDirectConnection
                    val connectionText = when {
                        connection.isConnected -> {
                            val role = if (connection.isGroupOwner) "GROUP OWNER" else "CLIENT"
                            "● P2P CONNECTED • $role • GO ${connection.groupOwnerIpAddress ?: "pending"}"
                        }
                        connection.isConnecting -> "◌ CONNECTING TO WI-FI DIRECT DEVICE"
                        connection.isDiscovering -> "◌ SCANNING FOR WI-FI DIRECT DEVICES"
                        else -> "○ WI-FI DIRECT READY"
                    }
                    Text(
                        text = connectionText,
                        color = if (connection.isConnected) ConnectedGreen else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    // Demo peers remain limited to the existing LAN view.
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.toggleShowDemoPeers() },
                        cornerRadius = 12.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.showDemoPeers) "● Demo Chat Rooms (Visible)" else "○ Real LAN Peers Only",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = if (uiState.showDemoPeers) "🗑 Clear Demo Rooms" else "↺ Restore Demo Rooms",
                                color = SignalBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pull-to-Refresh Container for Swipe Down Gesture
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = {
                        viewModel.refreshPeers(
                            if (isWifiDirectMode) onRefreshWifiDirectDiscovery else onRefreshDiscovery
                        )
                    },
                    state = pullToRefreshState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (displayPeers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📡", fontSize = 38.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Active Discovered Peers",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isWifiDirectMode) {
                                        "Pull down to scan or tap '↻' above.\nEnable Wi-Fi Direct on both nearby devices."
                                    } else {
                                        "Pull down to scan network or tap '↻' above.\nEnsure both devices share the same WiFi network."
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayPeers, key = { it.peerId }) { peer ->
                                PeerGlassListItem(peer = peer, onClick = {
                                    if (isWifiDirectMode) {
                                        wifiDirectPeersById[peer.peerId]?.let(onWifiDirectPeerClick)
                                    } else {
                                        onPeerClick(peer.peerId)
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeerGlassListItem(
    peer: ConnectedPeer,
    onClick: () -> Unit
) {
    val statusChipState = when (peer.status) {
        PeerStatus.CONNECTED -> StatusChipState.CONNECTED
        PeerStatus.RELAYING -> StatusChipState.RELAYING
        PeerStatus.CONNECTING -> StatusChipState.CONNECTING
        PeerStatus.DISCOVERED -> StatusChipState.WIFI
        else -> StatusChipState.LOST
    }

    val avatarBorderColor = when (peer.status) {
        PeerStatus.CONNECTED, PeerStatus.DISCOVERED -> ConnectedGreen
        PeerStatus.RELAYING -> RelayPurple
        PeerStatus.CONNECTING -> SignalBlue
        else -> ErrorRed
    }

    val subtext = if (peer.ipAddress != null) {
        "IP: ${peer.ipAddress.hostAddress}:${peer.port}"
    } else {
        "ID: ${peer.peerId.take(12)}"
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() },
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Circle with state-color border
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black, CircleShape)
                    .border(2.dp, avatarBorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.displayName.take(1).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            val repository = remember { com.hypershare.db.MessageRepository(context) }
            val isTrusted = remember(peer.peerId) { repository.isPeerTrusted(peer.peerId) }
            val unreadCount = remember(peer.peerId) { repository.getUnreadMessageCountForPeer(peer.peerId) }
            val lastMessage = remember(peer.peerId) { repository.getLastMessageForPeer(peer.peerId) }

            val formattedTime = remember(lastMessage?.timestamp) {
                if (lastMessage != null) {
                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastMessage.timestamp))
                } else null
            }

            val previewText = remember(lastMessage, subtext) {
                if (lastMessage != null) {
                    val prefix = if (lastMessage.isOutgoing) "You: " else ""
                    prefix + lastMessage.text
                } else {
                    if (isTrusted) {
                        "Verified Contact • $subtext"
                    } else {
                        val network = if (peer.mode == PeerMode.MODE_2_MESH) "Wi-Fi Direct" else "LAN"
                        "Discovered $network • $subtext"
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isTrusted) {
                        Text("• Verified", color = ConnectedGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("• Discovered", color = WarningAmber, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previewText,
                    color = if (unreadCount > 0) TextPrimary else TextSecondary,
                    fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (formattedTime != null) {
                    Text(
                        text = formattedTime,
                        color = if (unreadCount > 0) SignalBlue else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(SignalBlue, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    StatusChip(
                        state = statusChipState,
                        hopCount = peer.hopCount
                    )
                }
            }
        }
    }
}
