package com.hypershare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.theme.GlassBorder
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.SurfaceCard
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary

enum class NavTab {
    HOME,
    PEERS,
    ACCOUNT,
    SETTINGS
}

@Composable
fun BottomNavBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onOpenAccountSettings: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenQrScanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(SurfaceCard, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(width = 1.dp, color = GlassBorder, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gear Icon (⚙) -> App Settings
            Box(
                modifier = Modifier
                    .clickable { onOpenAppSettings() }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    color = if (selectedTab == NavTab.SETTINGS) SignalBlue else TextSecondary,
                    fontSize = 22.sp
                )
            }

            // Center Profile Icon (👤) -> Account Settings
            Box(
                modifier = Modifier
                    .clickable { onOpenAccountSettings() }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👤",
                    color = if (selectedTab == NavTab.ACCOUNT) SignalBlue else TextSecondary,
                    fontSize = 24.sp,
                    fontWeight = if (selectedTab == NavTab.ACCOUNT) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Scan / Home Icon (⛶) -> Open QR Scanner
            Box(
                modifier = Modifier
                    .clickable { if (onOpenQrScanner != {}) onOpenQrScanner() else onOpenHome() }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⛶",
                    color = if (selectedTab == NavTab.HOME) SignalBlue else TextSecondary,
                    fontSize = 24.sp,
                    fontWeight = if (selectedTab == NavTab.HOME) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
