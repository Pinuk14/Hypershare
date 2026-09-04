package com.hypershare.ui.group

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.model.GroupMessage
import com.hypershare.model.GroupType
import com.hypershare.ui.components.AmbientMeshGraphCanvas
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.GroupAvatarStack
import com.hypershare.ui.components.avatarColorFor
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.SurfaceCard
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary
import com.hypershare.ui.theme.WarningAmber
import com.hypershare.ui.theme.ErrorRed

@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val localUserId = viewModel.getLocalUserId()

    val displayMessages = uiState.messages.distinctBy { it.messageId }

    // Unregister active group chat on dispose
    DisposableEffect(uiState.groupId) {
        onDispose { viewModel.onDispose() }
    }

    // Auto-scroll to bottom
    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        AmbientMeshGraphCanvas()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            // Status bar spacer
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Header
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
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

                    Spacer(modifier = Modifier.width(10.dp))

                    // Group avatar
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(avatarColorFor(uiState.groupId), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.groupName.take(1).uppercase(),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.groupName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${uiState.members.size} members",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Admin menu
                    if (uiState.isAdmin) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x22FFFFFF), CircleShape)
                                    .clickable { showMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⋮", color = TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(SurfaceCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Broadcast Invite", color = TextPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        // Broadcast invite to all connected peers
                                        viewModel.broadcastInvite(emptyList())
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Dissolve Group", color = ErrorRed, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.dissolveGroup()
                                        showMenu = false
                                        onBackClick()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Inactive banner
            if (!uiState.isActive) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "This group has been dissolved",
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Date header
            Text(
                text = java.text.SimpleDateFormat("EEE, MMM d yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date()),
                color = WarningAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayMessages, key = { it.messageId }) { msg ->
                    GroupMessageBubble(
                        message = msg,
                        isOutgoing = msg.senderId == localUserId,
                        localUserId = localUserId
                    )
                }
            }

            // Input bar
            if (uiState.isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.onInputChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message group...", color = TextSecondary, fontSize = 14.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = SignalBlue,
                            focusedBorderColor = SignalBlue,
                            unfocusedBorderColor = Color(0x33FFFFFF)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.sendMessage() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("->", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    message: GroupMessage,
    isOutgoing: Boolean,
    localUserId: String
) {
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (isOutgoing) SignalBlue else Color(0xFF1A3A2A)
    val senderColor = avatarColorFor(message.senderId)

    val timeStr = remember(message.timestamp) {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(message.timestamp))
    }

    // Delivery status ticks for outgoing messages
    val tickStr = if (isOutgoing) {
        when (message.deliveryStatus) {
            "READ" -> " \u2713\u2713"
            "DELIVERED" -> " \u2713\u2713"
            else -> " \u2713"
        }
    } else ""

    val tickColor = if (message.deliveryStatus == "READ") ConnectedGreen else TextSecondary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // Sender name for incoming messages
                if (!isOutgoing) {
                    Text(
                        text = message.senderDisplayName,
                        color = senderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = timeStr,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (tickStr.isNotEmpty()) {
                        Text(
                            text = tickStr,
                            color = tickColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
