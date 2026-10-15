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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.hypershare.R
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.wifi.p2p.WifiP2pDevice
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
import com.hypershare.ui.theme.SignalBlue
import androidx.activity.compose.BackHandler
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
    onRefreshWifiDirectDiscovery: (() -> Unit)? = null,
    onBackClick: () -> Unit = onHomeClick,
    onOpenQrScanner: () -> Unit = {}
) {
    BackHandler {
        onBackClick()
    }

    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val isWifiDirectMode = uiState.currentMode == PeerMode.MODE_2_MESH
    val displayItems = uiState.conversationItems

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
                onOpenHome = onHomeClick,
                onOpenQrScanner = onOpenQrScanner
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

                // Glassmorphic Header Banner with Mode Status & Refresh Button
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    cornerRadius = 16.dp
                ) {
                    val totalUnreadCount = remember(context, displayItems) {
                        com.hypershare.db.MessageRepository(context).getTotalUnreadMessageCount()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x22FFFFFF), CircleShape)
                                    .clickable { onBackClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "HYPERSHARE",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                modifier = Modifier.clickable { onHomeClick() }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                                        text = "$totalUnreadCount",
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
                            Icon(
                                painter = painterResource(if (uiState.isRefreshing) R.drawable.ic_hourglass else R.drawable.ic_refresh),
                                contentDescription = "Refresh",
                                tint = SignalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (!isWifiDirectMode) "● LOCAL NETWORK CHATS (LAN)" else "▲ EMERGENCY MESH PEERS (P2P)",
                    color = if (!isWifiDirectMode) ConnectedGreen else ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
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
                        else -> "○ WI-FI DIRECT READY (MULTI-HOP ROUTING ACTIVE)"
                    }
                    Text(
                        text = connectionText,
                        color = if (connection.isConnected) ConnectedGreen else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        cornerRadius = 14.dp,
                        borderColor = Color(0x44F59E0B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x18F59E0B))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0x33F59E0B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_warning),
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "EMERGENCY MESH CONVERSATIONS",
                                    color = WarningAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "All chats preserved. Peers detected nearby on Wi-Fi Direct show a green outline.",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                    if (displayItems.isEmpty()) {
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
                                    text = if (isWifiDirectMode) "No Emergency Contacts" else "No Local Network Devices",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isWifiDirectMode) {
                                        "Only mutually accepted contacts can be messaged in Emergency Mode.\nExchange contact info via QR scanner or accept contacts in Local Mode."
                                    } else {
                                        "Pull down to scan network or tap '↻' above.\nEnsure devices are connected to the same Wi-Fi network."
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
                            items(displayItems, key = { it.peerId }) { item ->
                                PeerGlassListItem(
                                    item = item,
                                    isEmergencyMode = isWifiDirectMode,
                                    onClick = {
                                        onPeerClick(item.peerId)
                                    }
                                )
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
    item: PeerUiItem,
    isEmergencyMode: Boolean,
    onClick: () -> Unit
) {
    // Green outline indicates the peer is currently reachable (online on LAN or detected nearby via Wi-Fi Direct).
    val showGreenOutline = if (isEmergencyMode) {
        item.isNearbyWifiDirect
    } else {
        item.isOnlineOnLan
    }

    val cardBorderColor = if (showGreenOutline) ConnectedGreen else Color(0x14FFFFFF)
    val cardBorderWidth = if (showGreenOutline) 1.5.dp else 1.dp

    val formattedTime = remember(item.lastActivity) {
        formatTimestamp(item.lastActivity)
    }

    val previewText = remember(item.lastMessageSnippet, item.isOnlineOnLan, item.isNearbyWifiDirect, isEmergencyMode) {
        if (!item.lastMessageSnippet.isNullOrBlank()) {
            item.lastMessageSnippet
        } else if (isEmergencyMode) {
            if (item.isNearbyWifiDirect) "Nearby on Wi-Fi Direct • Ready to Link" else "Ready for Emergency Mesh Routing"
        } else if (item.isOnlineOnLan) {
            "Online on local network • ${item.ipAddress ?: ""}"
        } else {
            "Tap to open conversation"
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clickable { onClick() },
        cornerRadius = 14.dp,
        borderColor = cardBorderColor,
        borderWidth = cardBorderWidth
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clean, neutral avatar circle (conflicting colored avatar borders removed per design direction)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0x1AFFFFFF), CircleShape)
                    .border(1.dp, Color(0x22FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.displayName.take(1).uppercase(),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isTrusted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = "Verified Contact",
                            tint = ConnectedGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = previewText,
                    color = if (item.unreadCount > 0) TextPrimary else TextSecondary,
                    fontWeight = if (item.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (formattedTime.isNotEmpty()) {
                    Text(
                        text = formattedTime,
                        color = if (item.unreadCount > 0) ConnectedGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(ConnectedGreen, CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }

                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L

    return when {
        diff < oneDay -> {
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
        diff < 7 * oneDay -> {
            java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
        else -> {
            java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        }
    }
}
