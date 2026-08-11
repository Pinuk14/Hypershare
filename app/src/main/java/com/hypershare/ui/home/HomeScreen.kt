package com.hypershare.ui.home

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.PeerMode
import com.hypershare.ui.components.AmbientMeshGraphCanvas
import com.hypershare.ui.components.BottomNavBar
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.components.QrCodeCard
import com.hypershare.ui.components.StatusChip
import com.hypershare.ui.components.StatusChipState
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.TextPrimary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenLocalModeChats: () -> Unit,
    onOpenEmergencyModeChats: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenQrScanner: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val signedCardJson = remember(context) {
        val username = com.hypershare.application.UserIdentityManager.getInstance(context).getUsername()
        val identityManager = com.hypershare.identity.IdentityManager.getInstance(context)
        val card = com.hypershare.identity.ContactCard.createSignedCard(identityManager, username)
        card.toJson()
    }

    val totalUnreadCount = remember(context) {
        com.hypershare.db.MessageRepository(context).getTotalUnreadMessageCount()
    }

    Scaffold(
        containerColor = BackgroundBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomNavBar(
                selectedTab = NavTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.PEERS -> onOpenLocalModeChats()
                        NavTab.ACCOUNT -> onOpenAccountSettings()
                        NavTab.SETTINGS -> onOpenAppSettings()
                        NavTab.HOME -> {}
                    }
                },
                onOpenAppSettings = onOpenAppSettings,
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenHome = {},
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

                // Glassmorphic HYPERSHARE Header Banner
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    cornerRadius = 16.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HYPERSHARE",
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            StatusChip(
                                state = if (uiState.currentMode == PeerMode.MODE_1_WIFI) StatusChipState.WIFI else StatusChipState.MESH
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // LOCAL MODE vs EMERGENCY MODE Selector Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LOCAL MODE Card (Green Tone)
                    ModeCardButton(
                        title = "LOCAL MODE",
                        subtitle = "WiFi Local Network LAN",
                        containerColor = ConnectedGreen,
                        isSelected = uiState.currentMode == PeerMode.MODE_1_WIFI,
                        unreadCount = totalUnreadCount,
                        onClick = {
                            viewModel.selectMode(PeerMode.MODE_1_WIFI)
                            onOpenLocalModeChats()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // EMERGENCY MODE Card (Red Tone)
                    ModeCardButton(
                        title = "EMERGENCY",
                        subtitle = "Disaster Mesh Topology",
                        containerColor = ErrorRed,
                        isSelected = uiState.currentMode == PeerMode.MODE_2_MESH,
                        onClick = {
                            viewModel.selectMode(PeerMode.MODE_2_MESH)
                            onOpenEmergencyModeChats()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Glassmorphic QR Share ID Card Container
                GlassCard(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    cornerRadius = 20.dp
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        QrCodeCard(shareId = uiState.shareId, payload = signedCardJson)
                    }
                }
            }
        }
    }
}

@Composable
fun ModeCardButton(
    title: String,
    subtitle: String,
    containerColor: Color,
    isSelected: Boolean,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(115.dp)
            .clip(shape)
            .background(
                color = if (isSelected) containerColor.copy(alpha = 0.22f) else Color(0x0DFFFFFF),
                shape = shape
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) containerColor else Color(0x14FFFFFF),
                shape = shape
            )
            .clickable { onClick() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(com.hypershare.ui.theme.SignalBlue, androidx.compose.foundation.shape.CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) containerColor else TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}
