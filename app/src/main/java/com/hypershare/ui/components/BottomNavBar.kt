package com.hypershare.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.theme.SignalBlue
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SignalBlue, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gear Icon (⚙) -> App Settings
            Box(
                modifier = Modifier
                    .clickable { onOpenAppSettings() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙",
                    color = if (selectedTab == NavTab.SETTINGS) TextPrimary else TextSecondary,
                    fontSize = 20.sp
                )
            }

            // Center Profile Icon (👤) -> Account Settings
            Box(
                modifier = Modifier
                    .clickable { onOpenAccountSettings() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👤",
                    color = if (selectedTab == NavTab.ACCOUNT) TextPrimary else TextSecondary,
                    fontSize = 22.sp,
                    fontWeight = if (selectedTab == NavTab.ACCOUNT) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Scan / Home Icon (⛶) -> Home Screen
            Box(
                modifier = Modifier
                    .clickable { onOpenHome() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⛶",
                    color = if (selectedTab == NavTab.HOME) TextPrimary else TextSecondary,
                    fontSize = 22.sp,
                    fontWeight = if (selectedTab == NavTab.HOME) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
