package com.hypershare.ui.home

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.PeerMode
import com.hypershare.ui.components.BottomNavBar
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.components.QrCodeCard
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenLocalModeChats: () -> Unit,
    onOpenEmergencyModeChats: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
                onOpenHome = {}
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

            // Sleek HYPERSHARE Header Banner
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
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // LOCAL MODE vs EMERGENCY MODE Selector Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LOCAL MODE Card (Green)
                ModeCardButton(
                    title = "LOCAL\nMODE",
                    containerColor = ConnectedGreen,
                    isSelected = uiState.currentMode == PeerMode.MODE_1_WIFI,
                    onClick = {
                        viewModel.selectMode(PeerMode.MODE_1_WIFI)
                        onOpenLocalModeChats()
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // EMERGENCY MODE Card (Red)
                ModeCardButton(
                    title = "EMERGENCY\nMODE",
                    containerColor = ErrorRed,
                    isSelected = uiState.currentMode == PeerMode.MODE_2_MESH,
                    onClick = {
                        viewModel.selectMode(PeerMode.MODE_2_MESH)
                        onOpenEmergencyModeChats()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(44.dp))

            // Center QR Code "Share ID" Component
            QrCodeCard(shareId = uiState.shareId)
        }
    }
}

@Composable
fun ModeCardButton(
    title: String,
    containerColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .background(
                color = if (isSelected) containerColor else containerColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
