package com.hypershare.ui.chat

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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.StatusChip
import com.hypershare.ui.components.StatusChipState
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.SurfaceCard
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary
import com.hypershare.ui.theme.WarningAmber

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hypershare.ui.components.AmbientMeshGraphCanvas

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSecurityInfo by remember { mutableStateOf(false) }

    val displayMessages = uiState.messages.distinctBy { it.id }

    val listState = rememberLazyListState()

    // Register active chat room with LanSocketManager to suppress notifications and send READ_ACKs
    androidx.compose.runtime.DisposableEffect(uiState.peerId) {
        if (uiState.peerId.isNotEmpty() && uiState.peerId != "peer-default") {
            com.hypershare.service.LanSocketManager.getInstance().activeChatPeerId = uiState.peerId
            viewModel.markMessagesAsRead()
        }
        onDispose {
            if (com.hypershare.service.LanSocketManager.getInstance().activeChatPeerId == uiState.peerId) {
                com.hypershare.service.LanSocketManager.getInstance().activeChatPeerId = null
            }
        }
    }

    // Auto-scroll to bottom message when typing or opening screen
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    // Root Container with Ambient Mesh Graph Canvas in Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        // Ambient Network Pattern Background
        AmbientMeshGraphCanvas()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Status Bar Spacer Block
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(BackgroundBase)
            )

            // 2. Fixed Glassmorphic Top Header Bar
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Arrow Button
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

                    // Profile Pic Box (Non-back action per requirement 5)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", color = TextPrimary, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.peerName.ifEmpty { uiState.peerId },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val isMutualTrustEstablished = uiState.isPeerTrusted && uiState.hasPeerAcceptedUs
                            if (isMutualTrustEstablished) {
                                Text("• Mutual Contact", color = ConnectedGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else if (uiState.isPeerTrusted) {
                                Text("• Pending Accept", color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("• Discovered", color = TextSecondary, fontSize = 10.sp)
                            }
                        }
                        Text(
                            text = if (uiState.peerIpAddress.isNotEmpty()) "LAN • ${uiState.peerIpAddress}" else "AES-256-GCM • SECURE",
                            color = ConnectedGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // 3-Dots Options Menu Button
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x22FFFFFF), CircleShape)
                                .clickable { showMenu = !showMenu },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⋮", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Local Chat", color = TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    viewModel.clearChatHistory()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Security & Peer Info", color = TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    showSecurityInfo = true
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Message Request Banner for Un-paired / Non-Mutual Peers
            val isMutualTrustEstablished = uiState.isPeerTrusted && uiState.hasPeerAcceptedUs
            if (!isMutualTrustEstablished) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    cornerRadius = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val bannerTitle = when {
                            uiState.isPeerTrusted && !uiState.hasPeerAcceptedUs -> "Awaiting Peer Acceptance"
                            !uiState.isPeerTrusted && uiState.hasPeerAcceptedUs -> "Contact Accepted by Peer"
                            else -> "Message Request — Unverified Peer"
                        }

                        val bannerSubtitle = when {
                            uiState.isPeerTrusted && !uiState.hasPeerAcceptedUs ->
                                "You accepted ${uiState.peerName}. Waiting for ${uiState.peerName} to accept your contact request to unlock full two-way messaging."
                            !uiState.isPeerTrusted && uiState.hasPeerAcceptedUs ->
                                "${uiState.peerName} accepted & saved your contact! Tap Accept & Save Contact to complete mutual pairing."
                            else ->
                                "You have not exchanged QR contact info with this user.\nPreview allowance: 2 messages (max 300 chars each)."
                        }

                        Text(
                            text = bannerTitle,
                            color = if (uiState.hasPeerAcceptedUs) ConnectedGreen else WarningAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bannerSubtitle,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!uiState.isPeerTrusted) {
                                Button(
                                    onClick = { viewModel.acceptContactTrust() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Text("Accept & Save Contact", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0x22FFFFFF)),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Text("Waiting for Peer...", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = { onBackClick() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("Block / Ignore", color = TextPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 3. Conversation Date Header
            Text(
                text = java.text.SimpleDateFormat("EEE, MMM d yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                color = WarningAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // 4. Dynamically Resizing Messages List (weight 1f)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayMessages, key = { it.id }) { msg ->
                    GlassChatBubble(message = msg)
                }
            }

            // 5. Un-trusted Lock Notice / Live Character Count Indicator
            val isLimitReached = !isMutualTrustEstablished && uiState.untrustedOutgoingCount >= 2
            if (!isMutualTrustEstablished) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLimitReached) {
                        Text(
                            text = if (uiState.isPeerTrusted) "[!] Waiting for ${uiState.peerName} to Accept Contact to unlock unlimited messaging" else "[!] Un-paired Limit Reached (2/2 sent) — Receiver must Accept Contact",
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Un-paired Preview • ${uiState.inputText.length}/300 chars • ${uiState.untrustedOutgoingCount}/2 msgs sent",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 6. Glassmorphic Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.updateInputText(it) },
                    enabled = !isLimitReached,
                    placeholder = {
                        Text(
                            text = if (isLimitReached) "Limit Reached (2/2 sent)..." else "Type message...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalBlue,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        disabledBorderColor = Color(0x11FFFFFF),
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        disabledContainerColor = Color(0x11FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextSecondary
                    ),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = { viewModel.sendMessage() },
                    enabled = !isLimitReached && uiState.inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue, disabledContainerColor = Color(0x22FFFFFF)),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text("➢", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Security & Peer Info Dialog
    if (showSecurityInfo) {
        AlertDialog(
            onDismissRequest = { showSecurityInfo = false },
            title = { Text("Peer Security & Session Info", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Peer ID: ${uiState.peerId}", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("IP Address: ${uiState.peerIpAddress.ifEmpty { "127.0.0.1 (Local Mesh)" }}", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Protocol Port: 47200 (TCP)", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Encryption: AES-256-GCM (ECDH Key Exchange)", color = ConnectedGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityInfo = false }) {
                    Text("Close", color = SignalBlue)
                }
            },
            containerColor = SurfaceCard
        )
    }
}

@Composable
fun GlassChatBubble(message: ChatMessageItem) {
    val formattedTime = remember(message.timestamp) {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!message.isOutgoing) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", color = Color.White, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (message.isOutgoing) {
                // Outgoing Signal Blue Bubble (16dp radius, flat bottom-right)
                Box(
                    modifier = Modifier
                        .background(
                            color = SignalBlue,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            } else {
                // Incoming Glass Card Bubble (16dp radius, flat bottom-left)
                GlassCard(
                    cornerRadius = 16.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 3.dp, start = if (message.isOutgoing) 0.dp else 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedTime,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (message.isOutgoing) {
                Spacer(modifier = Modifier.width(4.dp))
                // 3-State Tick System (WhatsApp Style)
                when (message.status) {
                    MessageStatus.SENT -> {
                        // Single Grey Tick
                        Text(
                            text = "✓",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MessageStatus.DELIVERED -> {
                        // Double Grey Tick
                        Text(
                            text = "✓✓",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MessageStatus.READ -> {
                        // Double Green / Teal Tick
                        Text(
                            text = "✓✓",
                            color = ConnectedGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
