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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary

@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel,
    onHomeClick: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenSecurityPlayground: () -> Unit,
    onBackClick: () -> Unit = onHomeClick,
    onOpenPeers: () -> Unit = onHomeClick,
    onOpenQrScanner: () -> Unit = {}
) {
    BackHandler {
        onBackClick()
    }

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
                        NavTab.PEERS -> onOpenPeers()
                    }
                },
                onOpenAppSettings = {},
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenHome = onHomeClick,
                onOpenQrScanner = onOpenQrScanner
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

            // Header Banner with Back Arrow
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x22FFFFFF), CircleShape)
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "APP SETTINGS",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
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

            // Developer Options Toggle Card
            val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
            val forwardedPacketsCount by com.hypershare.service.LanSocketManager.getInstance().forwardedPacketsCount.collectAsState()

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
                            text = "Developer Options",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Enable hop inspection, transit metrics, and diagnostics",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isDeveloperMode,
                        onCheckedChange = { viewModel.setDeveloperMode(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextPrimary,
                            checkedTrackColor = SignalBlue,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = Color(0x33FFFFFF)
                        )
                    )
                }
            }

            // Developer Diagnostics & Metrics Panel (Strictly visible only when Developer Mode is ON)
            if (isDeveloperMode) {
                Spacer(modifier = Modifier.height(16.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    cornerRadius = 16.dp,
                    borderColor = Color(0x5538BDF8)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "DEVELOPER DIAGNOSTICS & METRICS",
                            color = SignalBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Packets Forwarded Metric
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Packets Forwarded (Relayed)",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Multi-hop packets routed via this node",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "$forwardedPacketsCount",
                                color = ConnectedGreen,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { com.hypershare.service.LanSocketManager.getInstance().resetForwardedPacketsCount() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text(
                                text = "Reset Forwarded Packet Counter",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { viewModel.clearAllAppData(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Text(
                                text = "⚠️ Wipe All App Data & Restart",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
