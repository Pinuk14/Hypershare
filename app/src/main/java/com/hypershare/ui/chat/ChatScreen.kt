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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.hypershare.R
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
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.model.TransferState
import com.hypershare.model.TransferJob
import com.hypershare.application.TransferManager
import com.hypershare.ui.filebrowser.openFileIntent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.produceState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import com.hypershare.ui.components.AmbientMeshGraphCanvas

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onConnectWifiDirect: (() -> Unit)? = null,
    wifiDirectConnection: com.hypershare.service.WifiDirectConnectionState? = null,
    isEmergencyMode: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showSecurityInfo by remember { mutableStateOf(false) }
    var showSharedMedia by remember { mutableStateOf(false) }
    var viewingMediaItem by remember { mutableStateOf<ChatMessageItem?>(null) }
    val photoRotations = remember { mutableStateMapOf<String, Float>() }

    // Intercept back gesture: dismiss modals before exiting chat
    BackHandler {
        when {
            viewingMediaItem != null -> viewingMediaItem = null
            showSharedMedia -> showSharedMedia = false
            showSecurityInfo -> showSecurityInfo = false
            showMenu -> showMenu = false
            else -> onBackClick()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val displayMessages = uiState.messages.distinctBy { it.id }
    val chatRows = remember(displayMessages) {
        groupMessagesIntoRows(displayMessages)
    }
    val listState = rememberLazyListState()

    // Observe active or recently completed file transfers for this peer
    val allTransferJobs by TransferManager.getInstance(context).jobs.collectAsState()
    val peerTransferJob = allTransferJobs.firstOrNull { job ->
        (job.destinationPeerId == uiState.peerId || job.sourcePeerId == uiState.peerId) &&
        (job.state == TransferState.IN_PROGRESS || job.state == TransferState.QUEUED || job.state == TransferState.COMPLETED || job.state == TransferState.FAILED)
    }

    val chatFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && uiState.peerId.isNotEmpty()) {
            val resolvedIp = uiState.peerIpAddress.ifEmpty {
                com.hypershare.service.LanSocketManager.getInstance().getPeerIp(uiState.peerId)
            }
            for (uri in uris) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                try {
                    TransferManager.getInstance(context).startSendTransfer(
                        fileUri = uri,
                        targetPeerId = uiState.peerId,
                        targetIp = resolvedIp,
                        context = context
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "Failed to start transfer for $uri", e)
                }
            }
            viewModel.refreshMessages()
        }
    }

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
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Profile Pic Box (Non-back action per requirement 5)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = "Peer Profile",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
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
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_image),
                                            contentDescription = null,
                                            tint = SignalBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Media", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showSharedMedia = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_folder),
                                            contentDescription = null,
                                            tint = SignalBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Send File (Quick Share)", color = SignalBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    chatFilePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )
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

            // Wi-Fi Direct Emergency Mesh Connection Status Banner (only shown in Emergency Mode)
            if (isEmergencyMode && uiState.peerIpAddress.isEmpty() && wifiDirectConnection != null) {
                val isP2pConnected = wifiDirectConnection.isConnected
                val isP2pConnecting = wifiDirectConnection.isConnecting
                val statusText = when {
                    isP2pConnected -> "● P2P Mesh Connected (Direct Link Active)"
                    isP2pConnecting -> "◌ Connecting Wi-Fi Direct Mesh..."
                    else -> "⚡ P2P Offline • Tap to Connect Directly"
                }
                val statusColor = when {
                    isP2pConnected -> ConnectedGreen
                    isP2pConnecting -> SignalBlue
                    else -> WarningAmber
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable(enabled = !isP2pConnected && !isP2pConnecting) {
                            onConnectWifiDirect?.invoke()
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                items(chatRows, key = { row ->
                    when (row) {
                        is ChatRowItem.SingleMessage -> row.message.id
                        is ChatRowItem.MediaAlbum -> "album_${row.messages.first().id}_${row.messages.size}"
                    }
                }) { row ->
                    when (row) {
                        is ChatRowItem.SingleMessage -> {
                            val msg = row.message
                            val matchingJob = allTransferJobs.firstOrNull { job ->
                                job.id == msg.transferId ||
                                (job.fileName == msg.mediaName && (job.destinationPeerId == uiState.peerId || job.sourcePeerId == uiState.peerId))
                            }
                            GlassChatBubble(
                                message = msg,
                                activeJob = matchingJob,
                                photoRotations = photoRotations,
                                onMediaClick = { viewingMediaItem = it },
                                onAcceptTransfer = { transferId ->
                                    TransferManager.getInstance(context).acceptTransfer(transferId)
                                },
                                onRejectTransfer = { transferId ->
                                    TransferManager.getInstance(context).rejectTransfer(transferId)
                                },
                                onCancelTransfer = { transferId ->
                                    TransferManager.getInstance(context).cancelTransfer(transferId, "Cancelled by user")
                                }
                            )
                        }
                        is ChatRowItem.MediaAlbum -> {
                            WhatsAppMediaAlbumBubble(
                                album = row.messages,
                                onMediaClick = { viewingMediaItem = it }
                            )
                        }
                    }
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

            // Active File Transfer Card (Fallback indicator only if transfer is not already represented in the chat stream)
            val isJobShownInChat = displayMessages.any { msg ->
                peerTransferJob != null && (
                    msg.transferId == peerTransferJob.id ||
                    (msg.mediaName == peerTransferJob.fileName && (peerTransferJob.destinationPeerId == uiState.peerId || peerTransferJob.sourcePeerId == uiState.peerId))
                )
            }
            if (peerTransferJob != null && !isJobShownInChat) {
                val job = peerTransferJob
                val isDone = job.state == TransferState.COMPLETED
                val isQueued = job.state == TransferState.QUEUED
                val isFailed = job.state == TransferState.FAILED
                val progress = if (isDone) 1f else job.progress

                val statusText = when {
                    isDone -> "Completed • Avg: ${TransferManager.getInstance().formatSpeed(job.averageSpeedBps)}"
                    isFailed -> "Failed: ${job.failureReason ?: "Cancelled or timed out"}"
                    isQueued && job.isOutgoing -> "Connecting & waiting for ${uiState.peerName} to accept..."
                    isQueued && !job.isOutgoing -> "Incoming file request (${TransferManager.getInstance().formatSpeed(job.fileSize)})"
                    job.isOutgoing -> "Sending ${(progress * 100).toInt()}% • ${TransferManager.getInstance().formatSpeed(job.currentSpeedBps)}"
                    else -> "Receiving ${(progress * 100).toInt()}% • ${TransferManager.getInstance().formatSpeed(job.currentSpeedBps)}"
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        isDone -> "✅"
                                        isFailed -> "⚠️"
                                        isQueued && !job.isOutgoing -> "📥"
                                        else -> "⚡"
                                    },
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = job.fileName,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = statusText,
                                        color = when {
                                            isDone -> ConnectedGreen
                                            isFailed -> ErrorRed
                                            isQueued -> WarningAmber
                                            else -> SignalBlue
                                        },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when {
                            isQueued && !job.isOutgoing -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { TransferManager.getInstance(context).rejectTransfer(job.id) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Decline", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { TransferManager.getInstance(context).acceptTransfer(job.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            isQueued && job.isOutgoing -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { TransferManager.getInstance(context).cancelTransfer(job.id, "Cancelled by user") },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                            }

                            job.state == TransferState.IN_PROGRESS -> {
                                Column {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = SignalBlue,
                                        trackColor = Color(0x33FFFFFF)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { TransferManager.getInstance(context).cancelTransfer(job.id, "Cancelled by user") },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                            }

                            isDone -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { TransferManager.getInstance(context).dismissJob(job.id) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Dismiss", fontSize = 11.sp)
                                    }
                                    if (job.fileUri != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { openFileIntent(context, job.fileUri, job.mimeType, job.fileName) },
                                            colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Open File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            isFailed -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { TransferManager.getInstance(context).dismissJob(job.id) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Dismiss", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
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

                Spacer(modifier = Modifier.width(8.dp))

                // File Transfer / Quick Share Button (Near send button)
                IconButton(
                    onClick = { chatFilePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(Color(0xFF2A3B5C), Color(0xFF1E293B))
                            ),
                            shape = CircleShape
                        )
                        .border(1.dp, SignalBlue.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder),
                        contentDescription = "Attach File",
                        tint = SignalBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

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

    // Shared Media Viewer Dialog
    if (showSharedMedia) {
        val sharedMedia = remember(uiState.messages) {
            uiState.messages.filter { it.mediaType != null }.reversed()
        }
        SharedMediaViewerDialog(
            peerName = uiState.peerName,
            mediaList = sharedMedia,
            onDismiss = { showSharedMedia = false },
            onOpenFile = { item ->
                if (item.mediaType == "video" || item.mediaType == "image") {
                    viewingMediaItem = item
                } else {
                    openFileIntent(
                        context = context,
                        uri = item.mediaUri?.let { Uri.parse(it) } ?: Uri.EMPTY,
                        mimeType = when (item.mediaType) {
                            "audio" -> "audio/*"
                            else -> "*/*"
                        },
                        fileName = item.mediaName ?: ""
                    )
                }
            },
            onShareFile = { item ->
                shareFileIntent(context, item.mediaUri, item.mediaName)
            }
        )
    }

    // Direct In-App Media Viewer Dialog (Photos & Videos with horizontal sliding)
    val allChatMediaItems = remember(uiState.messages) {
        uiState.messages.filter { it.mediaType == "image" || it.mediaType == "video" }
    }

    if (viewingMediaItem != null) {
        val initialMediaIndex = remember(viewingMediaItem, allChatMediaItems) {
            val idx = allChatMediaItems.indexOfFirst { it.id == viewingMediaItem!!.id }
            if (idx >= 0) idx else 0
        }
        InAppMediaViewerDialog(
            initialIndex = initialMediaIndex,
            mediaList = if (allChatMediaItems.isNotEmpty()) allChatMediaItems else listOf(viewingMediaItem!!),
            photoRotations = photoRotations,
            onDismiss = { viewingMediaItem = null },
            onMediaViewed = { item ->
                viewModel.markMessageAsRead(item)
            }
        )
    }
}

@Composable
fun GlassChatBubble(
    message: ChatMessageItem,
    activeJob: TransferJob? = null,
    photoRotations: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Float>? = null,
    onMediaClick: ((ChatMessageItem) -> Unit)? = null,
    onAcceptTransfer: ((String) -> Unit)? = null,
    onRejectTransfer: ((String) -> Unit)? = null,
    onCancelTransfer: ((String) -> Unit)? = null
) {
    val formattedTime = remember(message.timestamp) {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            if (!message.isOutgoing) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (message.mediaType != null) {
                WhatsAppMediaBubble(
                    message = message,
                    activeJob = activeJob,
                    photoRotations = photoRotations,
                    onMediaClick = onMediaClick,
                    onAcceptTransfer = onAcceptTransfer,
                    onRejectTransfer = onRejectTransfer,
                    onCancelTransfer = onCancelTransfer
                )
            } else if (message.isOutgoing) {
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

        if (message.mediaType == null) {
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
                            Text(
                                text = "✓",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        MessageStatus.DELIVERED -> {
                            Text(
                                text = "✓✓",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        MessageStatus.READ -> {
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
}

@Composable
fun WhatsAppMediaBubble(
    message: ChatMessageItem,
    activeJob: TransferJob?,
    photoRotations: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Float>? = null,
    onMediaClick: ((ChatMessageItem) -> Unit)? = null,
    onAcceptTransfer: ((String) -> Unit)?,
    onRejectTransfer: ((String) -> Unit)?,
    onCancelTransfer: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val isVideo = message.mediaType == "video"
    val isImage = message.mediaType == "image"
    val isAudio = message.mediaType == "audio"
    val isVisual = isVideo || isImage
    val formattedTime = remember(message.timestamp) {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
    }

    // Asynchronously decode thumbnail for video and image via MediaStore/ContentResolver
    val thumbnailBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, message.mediaUri) {
        if (!message.mediaUri.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(
                        Uri.parse(message.mediaUri),
                        android.util.Size(480, 320),
                        null
                    )
                } catch (_: Exception) { null }
            }
        }
    }

    val bubbleShape = if (message.isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    val canOpen = activeJob == null || activeJob.state == TransferState.COMPLETED
    val sizeText = remember(message.mediaSize) {
        TransferManager.getInstance().formatSize(message.mediaSize)
    }

    Box(
        modifier = Modifier
            .width(260.dp)
            .clip(bubbleShape)
            .background(
                if (message.isOutgoing) {
                    Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8)))
                } else {
                    Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                }
            )
            .border(
                1.dp,
                if (message.isOutgoing) Color(0x4460A5FA) else Color(0x22FFFFFF),
                bubbleShape
            )
            .clickable(enabled = canOpen) {
                if (isVideo || isImage) {
                    onMediaClick?.invoke(message)
                } else {
                    val uriToOpen = message.mediaUri?.let { Uri.parse(it) } ?: Uri.EMPTY
                    val mime = when (message.mediaType) {
                        "audio" -> "audio/*"
                        else -> "*/*"
                    }
                    openFileIntent(context, uriToOpen, mime, message.mediaName ?: "")
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isVisual) {
                // Video & Photo Visual Preview Block
                val currentRot = photoRotations?.get(message.id) ?: 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFF0B1120)),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailBitmap != null) {
                        Image(
                            bitmap = thumbnailBitmap!!.asImageBitmap(),
                            contentDescription = message.mediaName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = currentRot }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x55000000), Color.Transparent, Color(0x88000000))
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            if (isVideo) Color(0x33EF4444) else Color(0x3310B981),
                                            Color(0xFF090D16)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (isVideo) R.drawable.ic_videocam else R.drawable.ic_image),
                                contentDescription = null,
                                tint = if (isVideo) Color(0xFFEF4444) else Color(0xFF10B981),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Top Type Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(if (isVideo) R.drawable.ic_videocam else R.drawable.ic_image),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isVideo) "VIDEO" else "PHOTO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Top Size Badge
                    if (message.mediaSize > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sizeText,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Centered Circular Play Button for Video
                    if (isVideo && canOpen) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color(0xAA000000), CircleShape)
                                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_arrow),
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // In-Chat Photo Rotation Button (Bottom right overlay)
                    if (isImage && canOpen && photoRotations != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(30.dp)
                                .background(Color(0xCC000000), CircleShape)
                                .clickable {
                                    val nextRot = (currentRot + 90f) % 360f
                                    photoRotations[message.id] = nextRot
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_rotate_right),
                                contentDescription = "Rotate Photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Live In-Flight Transfer Progress Overlay
                    if (activeJob != null && activeJob.state == TransferState.IN_PROGRESS) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xDD000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "${(activeJob.progress * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { activeJob.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = SignalBlue,
                                    trackColor = Color(0x44FFFFFF)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = TransferManager.getInstance().formatSpeed(activeJob.currentSpeedBps),
                                    color = SignalBlue,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else if (isAudio) {
                // Audio Track Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22A855F7))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x33A855F7), CircleShape)
                            .border(1.dp, Color(0x66A855F7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_music_note),
                            contentDescription = "Audio Track",
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.mediaName ?: "Audio Track",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Audio • $sizeText",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                // Document / File Header
                val ext = (message.mediaName ?: "").substringAfterLast('.', "DOC").uppercase()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x223B82F6))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0x333B82F6), RoundedCornerShape(10.dp))
                            .border(1.dp, SignalBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_description),
                                contentDescription = "Document",
                                tint = SignalBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = ext.take(4),
                                color = SignalBlue,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.mediaName ?: "Document",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$ext • $sizeText",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Bottom Filename & Actions Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isVisual) {
                    Text(
                        text = message.mediaName ?: "Media File",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    // Queued Incoming: Accept / Decline
                    activeJob != null && activeJob.state == TransferState.QUEUED && !activeJob.isOutgoing -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onRejectTransfer?.invoke(activeJob.id) },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Decline", color = ErrorRed, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onAcceptTransfer?.invoke(activeJob.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Accept", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Queued Outgoing: Waiting for peer
                    activeJob != null && activeJob.state == TransferState.QUEUED && activeJob.isOutgoing -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Waiting for peer...",
                                color = WarningAmber,
                                fontSize = 10.sp
                            )
                            TextButton(
                                onClick = { onCancelTransfer?.invoke(activeJob.id) },
                                modifier = Modifier.height(26.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Cancel", color = ErrorRed, fontSize = 10.sp)
                            }
                        }
                    }

                    // In Progress: Progress bar and cancel (for non-visual items)
                    activeJob != null && activeJob.state == TransferState.IN_PROGRESS && !isVisual -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { activeJob.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = SignalBlue,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(activeJob.progress * 100).toInt()}% • ${TransferManager.getInstance().formatSpeed(activeJob.currentSpeedBps)}",
                                color = SignalBlue,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            TextButton(
                                onClick = { onCancelTransfer?.invoke(activeJob.id) },
                                modifier = Modifier.height(26.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Cancel", color = ErrorRed, fontSize = 10.sp)
                            }
                        }
                    }

                    // Failed Transfer
                    activeJob != null && activeJob.state == TransferState.FAILED -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "⚠️ Failed: ${activeJob.failureReason ?: "Transfer interrupted"}",
                            color = ErrorRed,
                            fontSize = 10.sp
                        )
                    }

                    // Ready / Completed: Tap to open prompt
                    canOpen -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isVideo) "Tap to play video" else "Tap to open",
                                color = if (message.isOutgoing) Color(0xDDFFFFFF) else ConnectedGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formattedTime,
                                    color = if (message.isOutgoing) Color(0xBBFFFFFF) else TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (message.isOutgoing) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    when (message.status) {
                                        MessageStatus.SENT -> Text("✓", color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        MessageStatus.DELIVERED -> Text("✓✓", color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        MessageStatus.READ -> Text("✓✓", color = ConnectedGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!canOpen) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedTime,
                            color = if (message.isOutgoing) Color(0xBBFFFFFF) else TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (message.isOutgoing) {
                            Spacer(modifier = Modifier.width(3.dp))
                            when (message.status) {
                                MessageStatus.SENT -> Text("✓", color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                MessageStatus.DELIVERED -> Text("✓✓", color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                MessageStatus.READ -> Text("✓✓", color = ConnectedGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedMediaViewerDialog(
    peerName: String,
    mediaList: List<ChatMessageItem>,
    onDismiss: () -> Unit,
    onOpenFile: (ChatMessageItem) -> Unit,
    onShareFile: (ChatMessageItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = listOf("ALL", "VIDEOS", "PHOTOS", "AUDIO", "DOCS")

    val filteredList = remember(mediaList, selectedCategory) {
        when (selectedCategory) {
            "VIDEOS" -> mediaList.filter { it.mediaType == "video" }
            "PHOTOS" -> mediaList.filter { it.mediaType == "image" }
            "AUDIO" -> mediaList.filter { it.mediaType == "audio" }
            "DOCS" -> mediaList.filter { it.mediaType == "document" }
            else -> mediaList
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBase)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).background(Color(0x22FFFFFF), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shared Media",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$peerName • ${mediaList.size} item${if (mediaList.size == 1) "" else "s"}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Category Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        val count = when (cat) {
                            "VIDEOS" -> mediaList.count { it.mediaType == "video" }
                            "PHOTOS" -> mediaList.count { it.mediaType == "image" }
                            "AUDIO" -> mediaList.count { it.mediaType == "audio" }
                            "DOCS" -> mediaList.count { it.mediaType == "document" }
                            else -> mediaList.size
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) SignalBlue else Color(0x22FFFFFF))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "$cat ($count)",
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Media Items List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No media found in this category",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            SharedMediaItemCard(
                                item = item,
                                onOpen = { onOpenFile(item) },
                                onShare = { onShareFile(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedMediaItemCard(
    item: ChatMessageItem,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val formattedDate = remember(item.timestamp) {
        java.text.SimpleDateFormat("MMM d, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
    }
    val sizeText = remember(item.mediaSize) {
        TransferManager.getInstance().formatSize(item.mediaSize)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Mini Thumbnail
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        when (item.mediaType) {
                            "video" -> Color(0x33EF4444)
                            "image" -> Color(0x3310B981)
                            "audio" -> Color(0x33A855F7)
                            else -> Color(0x333B82F6)
                        },
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        when (item.mediaType) {
                            "video" -> R.drawable.ic_videocam
                            "image" -> R.drawable.ic_image
                            "audio" -> R.drawable.ic_music_note
                            else -> R.drawable.ic_description
                        }
                    ),
                    contentDescription = null,
                    tint = when (item.mediaType) {
                        "video" -> Color(0xFFEF4444)
                        "image" -> Color(0xFF10B981)
                        "audio" -> Color(0xFFA855F7)
                        else -> SignalBlue
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.mediaName ?: "Media File",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (item.isOutgoing) "Sent" else "Received",
                        color = if (item.isOutgoing) SignalBlue else ConnectedGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "•",
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = sizeText,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = formattedDate,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp).background(Color(0x22FFFFFF), CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = "Share",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text("Open", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun shareFileIntent(context: Context, uriString: String?, fileName: String?) {
    if (uriString.isNullOrEmpty()) return
    try {
        val uri = Uri.parse(uriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${fileName ?: "file"}"))
    } catch (_: Exception) {}
}

// -------------------------------------------------------------
// WhatsApp Media Album (Clubbing) & Direct In-App Viewer Models
// -------------------------------------------------------------

sealed class ChatRowItem {
    data class SingleMessage(val message: ChatMessageItem) : ChatRowItem()
    data class MediaAlbum(val messages: List<ChatMessageItem>) : ChatRowItem()
}

fun groupMessagesIntoRows(messages: List<ChatMessageItem>): List<ChatRowItem> {
    if (messages.isEmpty()) return emptyList()
    val rows = mutableListOf<ChatRowItem>()
    val currentAlbum = mutableListOf<ChatMessageItem>()

    fun flushAlbum() {
        if (currentAlbum.isNotEmpty()) {
            if (currentAlbum.size == 1) {
                rows.add(ChatRowItem.SingleMessage(currentAlbum[0]))
            } else {
                rows.add(ChatRowItem.MediaAlbum(currentAlbum.toList()))
            }
            currentAlbum.clear()
        }
    }

    for (msg in messages) {
        val isVisual = msg.mediaType == "image" || msg.mediaType == "video"
        if (isVisual) {
            if (currentAlbum.isEmpty()) {
                currentAlbum.add(msg)
            } else {
                val prev = currentAlbum.last()
                val sameSender = prev.isOutgoing == msg.isOutgoing && prev.senderId == msg.senderId
                val timeDiff = Math.abs(msg.timestamp - prev.timestamp)
                if (sameSender && timeDiff <= 120_000L) {
                    currentAlbum.add(msg)
                } else {
                    flushAlbum()
                    currentAlbum.add(msg)
                }
            }
        } else {
            flushAlbum()
            rows.add(ChatRowItem.SingleMessage(msg))
        }
    }
    flushAlbum()
    return rows
}

@Composable
fun WhatsAppMediaAlbumBubble(
    album: List<ChatMessageItem>,
    onMediaClick: (ChatMessageItem) -> Unit
) {
    val isOutgoing = album.first().isOutgoing
    val formattedTime = remember(album.last().timestamp) {
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(album.last().timestamp))
    }
    val status = if (album.any { it.status == MessageStatus.READ }) MessageStatus.READ else album.last().status

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!isOutgoing) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(260.dp)
                    .clip(bubbleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.dp, if (isOutgoing) Color(0x4460A5FA) else Color(0x22FFFFFF), bubbleShape)
            ) {
                val count = album.size
                if (count == 2) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AlbumTile(
                            item = album[0],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onMediaClick(album[0]) }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        AlbumTile(
                            item = album[1],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onMediaClick(album[1]) }
                        )
                    }
                } else if (count == 3) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AlbumTile(
                            item = album[0],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onMediaClick(album[0]) }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AlbumTile(
                                item = album[1],
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                onClick = { onMediaClick(album[1]) }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AlbumTile(
                                item = album[2],
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                onClick = { onMediaClick(album[2]) }
                            )
                        }
                    }
                } else {
                    // 4 or more items: 2x2 grid with +N on the 4th tile
                    val remainingCount = count - 3
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            AlbumTile(
                                item = album[0],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onMediaClick(album[0]) }
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            AlbumTile(
                                item = album[1],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onMediaClick(album[1]) }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            AlbumTile(
                                item = album[2],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onMediaClick(album[2]) }
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            AlbumTile(
                                item = album[3],
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                overlayCount = if (remainingCount > 1) remainingCount else null,
                                onClick = { onMediaClick(album[3]) }
                            )
                        }
                    }
                }

                // WhatsApp-style timestamp & tick badge floating at bottom right of the album
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedTime,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (isOutgoing) {
                            Spacer(modifier = Modifier.width(3.dp))
                            when (status) {
                                MessageStatus.SENT -> Text("✓", color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                MessageStatus.DELIVERED -> Text("✓✓", color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                MessageStatus.READ -> Text("✓✓", color = ConnectedGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumTile(
    item: ChatMessageItem,
    modifier: Modifier = Modifier,
    overlayCount: Int? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = item.mediaType == "video"

    val thumbnailBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, item.mediaUri) {
        if (!item.mediaUri.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(
                        Uri.parse(item.mediaUri),
                        android.util.Size(320, 320),
                        null
                    )
                } catch (_: Exception) { null }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1E293B))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = item.mediaName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(if (isVideo) R.drawable.ic_videocam else R.drawable.ic_image),
                contentDescription = null,
                tint = if (isVideo) Color(0xFFEF4444) else Color(0xFF10B981),
                modifier = Modifier.size(28.dp)
            )
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0x88000000), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (overlayCount != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overlayCount",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun InAppMediaViewerDialog(
    initialIndex: Int,
    mediaList: List<ChatMessageItem>,
    photoRotations: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Float>? = null,
    onDismiss: () -> Unit,
    onMediaViewed: ((ChatMessageItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0)),
        pageCount = { mediaList.size }
    )

    val currentItem = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()

    // Automatically trigger markAsRead on peer item when viewed
    LaunchedEffect(pagerState.currentPage) {
        mediaList.getOrNull(pagerState.currentPage)?.let {
            onMediaViewed?.invoke(it)
        }
    }

    val isVideo = currentItem.mediaType == "video"
    val formattedDate = remember(currentItem.timestamp) {
        java.text.SimpleDateFormat("MMM d, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(currentItem.timestamp))
    }
    val sizeText = remember(currentItem.mediaSize) {
        TransferManager.getInstance().formatSize(currentItem.mediaSize)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem.mediaName ?: "Media Preview",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (mediaList.size > 1) {
                                "${pagerState.currentPage + 1} of ${mediaList.size} • $sizeText"
                            } else {
                                "$sizeText • $formattedDate"
                            },
                            color = Color(0xBBFFFFFF),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // ONLY ONE "Open with..." button in the entire viewer!
                    Button(
                        onClick = {
                            openFileIntent(
                                context = context,
                                uri = currentItem.mediaUri?.let { Uri.parse(it) } ?: Uri.EMPTY,
                                mimeType = if (isVideo) "video/*" else if (currentItem.mediaType == "image") "image/*" else "*/*",
                                fileName = currentItem.mediaName ?: ""
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Open with...", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_open_in_new),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Center Swipable Pager with Left/Right chevrons
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    var isCurrentPhotoZoomed by remember { mutableStateOf(false) }
                    LaunchedEffect(pagerState.currentPage) {
                        isCurrentPhotoZoomed = false
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !isCurrentPhotoZoomed,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = mediaList[page]
                        val isPageVideo = item.mediaType == "video"
                        val isCurrentPage = (page == pagerState.currentPage)

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPageVideo) {
                                val videoUri = item.mediaUri?.let { Uri.parse(it) }
                                if (videoUri != null) {
                                    ModernVideoPlayer(
                                        videoUri = videoUri,
                                        isCurrentPage = (page == pagerState.currentPage),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("Video file not available", color = Color.White)
                                }
                            } else if (item.mediaType == "image") {
                                val imageBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, item.mediaUri) {
                                    if (!item.mediaUri.isNullOrEmpty()) {
                                        value = withContext(Dispatchers.IO) {
                                            try {
                                                val parsed = Uri.parse(item.mediaUri)
                                                context.contentResolver.openInputStream(parsed)?.use { stream ->
                                                    android.graphics.BitmapFactory.decodeStream(stream)
                                                }
                                            } catch (_: Exception) { null }
                                        }
                                    }
                                }

                                if (imageBitmap != null) {
                                    var scale by remember(item.id) { mutableFloatStateOf(1f) }
                                    var offsetX by remember(item.id) { mutableFloatStateOf(0f) }
                                    var offsetY by remember(item.id) { mutableFloatStateOf(0f) }
                                    var rotation by remember(item.id) {
                                        mutableFloatStateOf(photoRotations?.get(item.id) ?: 0f)
                                    }

                                    LaunchedEffect(scale, isCurrentPage) {
                                        if (isCurrentPage) {
                                            isCurrentPhotoZoomed = (scale > 1.05f)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(item.id) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        val pointerCount = event.changes.count { it.pressed }
                                                        if (pointerCount >= 2) {
                                                            // Multi-touch pinch to zoom
                                                            val zoomChange = event.calculateZoom()
                                                            val panChange = event.calculatePan()
                                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                                                scale = newScale
                                                                if (newScale > 1f) {
                                                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                                                    offsetX = (offsetX + panChange.x * newScale).coerceIn(-maxOffsetX, maxOffsetX)
                                                                    offsetY = (offsetY + panChange.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                                                                } else {
                                                                    offsetX = 0f
                                                                    offsetY = 0f
                                                                }
                                                                event.changes.forEach { it.consume() }
                                                            }
                                                        } else if (pointerCount == 1 && scale > 1.05f) {
                                                            // 1 finger pan while zoomed in
                                                            val panChange = event.calculatePan()
                                                            if (panChange != Offset.Zero) {
                                                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                                                offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                                                offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                                                event.changes.forEach { it.consume() }
                                                            }
                                                        }
                                                        // When scale <= 1.05f and pointerCount == 1:
                                                        // Do NOT consume pointer events, allowing HorizontalPager to swipe forward and backward
                                                    } while (event.changes.any { it.pressed })
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = imageBitmap!!.asImageBitmap(),
                                            contentDescription = item.mediaName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationX = offsetX
                                                    translationY = offsetY
                                                    rotationZ = rotation
                                                }
                                        )

                                        // Floating Photo Zoom & Rotate Controls Pill at bottom
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 16.dp)
                                                .background(Color(0xCC1E293B), RoundedCornerShape(24.dp))
                                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val newScale = (scale - 0.5f).coerceAtLeast(1f)
                                                        scale = newScale
                                                        if (newScale == 1f) {
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_zoom_out),
                                                        contentDescription = "Zoom Out",
                                                        tint = if (scale > 1f) Color.White else Color(0x55FFFFFF),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Text(
                                                    text = "${(scale * 100).toInt()}%",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable {
                                                            scale = 1f
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                        }
                                                        .padding(horizontal = 6.dp)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        scale = (scale + 0.5f).coerceAtMost(5f)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_zoom_in),
                                                        contentDescription = "Zoom In",
                                                        tint = if (scale < 5f) Color.White else Color(0x55FFFFFF),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .height(18.dp)
                                                        .background(Color(0x44FFFFFF))
                                                )

                                                IconButton(
                                                    onClick = {
                                                        val nextRot = (rotation + 90f) % 360f
                                                        rotation = nextRot
                                                        photoRotations?.set(item.id, nextRot)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_rotate_right),
                                                        contentDescription = "Rotate",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    CircularProgressIndicator(color = SignalBlue)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_description),
                                        contentDescription = null,
                                        tint = SignalBlue,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(item.mediaName ?: "Document", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            openFileIntent(
                                                context = context,
                                                uri = item.mediaUri?.let { Uri.parse(it) } ?: Uri.EMPTY,
                                                mimeType = "*/*",
                                                fileName = item.mediaName ?: ""
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Open with...", color = Color.White, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                painter = painterResource(R.drawable.ic_open_in_new),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Prev / Next Arrow Overlay for fast navigation
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .size(42.dp)
                                .background(Color(0x66000000), CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (pagerState.currentPage < mediaList.size - 1) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .size(42.dp)
                                .background(Color(0x66000000), CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = 180f }
                            )
                        }
                    }
                }

                // Bottom Action Bar: Only Share & Counter Pill (NO duplicate 'Open with...')
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { shareFileIntent(context, currentItem.mediaUri, currentItem.mediaName) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (mediaList.size > 1) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernVideoPlayer(
    videoUri: Uri,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier
) {
    var videoView by remember { mutableStateOf<android.widget.VideoView?>(null) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    // When page changes or unmounts, pause playback
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            videoView?.pause()
            isPlaying = false
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            videoView?.stopPlayback()
        }
    }

    // Auto-update timeline position while playing
    LaunchedEffect(isPlaying, isDragging) {
        while (isPlaying && !isDragging) {
            videoView?.let { vv ->
                currentPositionMs = vv.currentPosition.toLong()
                if (durationMs <= 0 && vv.duration > 0) {
                    durationMs = vv.duration.toLong()
                }
            }
            delay(200)
        }
    }

    // Auto-hide controls overlay after 3.5 seconds while playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        // Native Android VideoView (wrapContentHeight centered to preserve native aspect ratio)
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mediaPlayer = mp
                        durationMs = mp.duration.toLong()
                        isPrepared = true
                        if (isMuted) mp.setVolume(0f, 0f)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
                            try {
                                val params = mp.playbackParams.setSpeed(playbackSpeed)
                                mp.playbackParams = params
                            } catch (_: Exception) {}
                        }
                        if (isCurrentPage) {
                            start()
                            isPlaying = true
                        }
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = durationMs
                        showControls = true
                    }
                    videoView = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )

        if (!isPrepared) {
            CircularProgressIndicator(color = SignalBlue)
        }

        // Overlay Controls (Fade In / Out)
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
            ) {
                // Top Action Row (Replay / Rewind button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            videoView?.seekTo(0)
                            currentPositionMs = 0L
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x44000000), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_replay),
                            contentDescription = "Restart Video",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Large Play/Pause Toggle button (easy tapping)
                IconButton(
                    onClick = {
                        videoView?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0x66000000), CircleShape)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Bottom Modern Control Bar (Play/Pause, Timeline Scrubber, Speed, Volume)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Time labels (current / duration)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayPos = if (isDragging) dragPositionMs.toLong() else currentPositionMs
                        Text(
                            text = formatVideoTime(displayPos),
                            color = Color(0xDDFFFFFF),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatVideoTime(durationMs),
                            color = Color(0x99FFFFFF),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Main Timeline Row matching the image
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause icon button
                        IconButton(
                            onClick = {
                                videoView?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Draggable Scrubber Timeline
                        Slider(
                            value = if (isDragging) dragPositionMs else currentPositionMs.toFloat().coerceIn(0f, (if (durationMs > 0) durationMs.toFloat() else 1f)),
                            onValueChange = { newPos ->
                                isDragging = true
                                dragPositionMs = newPos
                            },
                            onValueChangeFinished = {
                                videoView?.seekTo(dragPositionMs.toInt())
                                currentPositionMs = dragPositionMs.toLong()
                                isDragging = false
                            },
                            valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color(0x55FFFFFF)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Playback Speed button (1.0x, 1.25x, 1.5x, 2.0x, 0.5x)
                        Box(
                            modifier = Modifier
                                .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .clickable {
                                    val nextSpeed = when (playbackSpeed) {
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.5f
                                        else -> 1.0f
                                    }
                                    playbackSpeed = nextSpeed
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        try {
                                            mediaPlayer?.let { mp ->
                                                val params = mp.playbackParams.setSpeed(nextSpeed)
                                                mp.playbackParams = params
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Mute / Unmute Volume button
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                val vol = if (isMuted) 0f else 1f
                                mediaPlayer?.setVolume(vol, vol)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatVideoTime(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
}

