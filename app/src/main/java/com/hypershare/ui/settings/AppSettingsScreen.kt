package com.hypershare.ui.settings

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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.components.BottomNavBar
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.SignalBlue
import android.content.pm.ApplicationInfo
import androidx.compose.ui.platform.LocalContext
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary

@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel,
    onHomeClick: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenSecurityPlayground: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = BackgroundBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomNavBar(
                selectedTab = NavTab.SETTINGS,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.HOME -> onHomeClick()
                        NavTab.ACCOUNT -> onOpenAccountSettings()
                        NavTab.SETTINGS -> {}
                        NavTab.PEERS -> onHomeClick()
                    }
                },
                onOpenAppSettings = {},
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
            // Status Bar Spacer Block
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(BackgroundBase)
            )

            // Header Banner
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
                    Text(
                        text = "APP SETTINGS",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.clickable { onHomeClick() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OLED Dark Theme Enforcement Notice
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "OLED DARK THEME",
                        color = SignalBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Battery-efficient deep space black (#0A0A0F) optimized for emergency use cases.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-Switch Emergency Mode Setting
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Emergency Mesh Mode",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Auto-switch to Mode 2 when LAN drops",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = uiState.autoSwitchEmergencyMode,
                        onCheckedChange = { viewModel.toggleAutoSwitch(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = MeshTeal,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color(0x33FFFFFF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Playground Action Button
            Button(
                onClick = { onOpenSecurityPlayground() },
                colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = "🔒 Launch Security & Protocol Playground",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Developer Data Wipe Feature (Visible in testing/debug mode only)
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                Spacer(modifier = Modifier.height(24.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    cornerRadius = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "DEVELOPER OPTIONS (DEBUG ONLY)",
                            color = Color(0xFFFF5555),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Simulate a fresh install by erasing all databases, preferences, keys, and cached files.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.clearAllAppData(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                text = "⚠️ Wipe All App Data & Restart",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
