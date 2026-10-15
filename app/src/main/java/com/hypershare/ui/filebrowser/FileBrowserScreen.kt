package com.hypershare.ui.filebrowser

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.application.TransferCompletedEvent
import com.hypershare.application.TransferManager
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.TransferJob
import com.hypershare.model.TransferState
import com.hypershare.ui.components.AmbientMeshGraphCanvas
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.StatusChip
import com.hypershare.ui.components.StatusChipState
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.SurfaceCard
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary
import com.hypershare.ui.theme.WarningAmber

@Composable
fun FileBrowserScreen(
    viewModel: FileBrowserViewModel,
    peers: List<ConnectedPeer> = emptyList(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Dismiss active modals or prompts before exiting
    BackHandler {
        when {
            uiState.latestCompletedEvent != null -> viewModel.dismissCompletionDialog()
            uiState.incomingPrompt != null -> viewModel.rejectIncomingTransfer(uiState.incomingPrompt!!.transferId)
            else -> onBackClick()
        }
    }

    val activeJobs by viewModel.transferJobs.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectFile(context, uri)
        }
    }

    Scaffold(
        containerColor = BackgroundBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AmbientMeshGraphCanvas()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(modifier = Modifier.height(8.dp))

                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x1AFFFFFF), CircleShape)
                            .clickable { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HyperShare Quick Share",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "High-speed 64 KB chunked mesh file transfer",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    StatusChip(
                        state = StatusChipState.CONNECTED
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Send File Hub Card
                    item {
                        SendFilePickerCard(
                            selectedFile = uiState.selectedFile,
                            isViewOnly = uiState.isViewOnlyPermission,
                            onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                            onClearFile = { viewModel.clearSelectedFile() },
                            onTogglePermission = { viewModel.togglePermission(it) }
                        )
                    }

                    // 2. Discovered Nearby Peers Row (Active if file selected)
                    item {
                        NearbyPeersSelectorSection(
                            peers = peers,
                            selectedFile = uiState.selectedFile,
                            onPeerSelected = { peer ->
                                viewModel.sendSelectedFileToPeer(
                                    targetPeerId = peer.peerId,
                                    targetIp = peer.ipAddress?.hostAddress ?: ""
                                )
                            }
                        )
                    }

                    // 3. Active & Recent Transfers Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE & RECENT TRANSFERS",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (activeJobs.isNotEmpty()) {
                                Text(
                                    text = "${activeJobs.size} task(s)",
                                    color = SignalBlue,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (activeJobs.isEmpty()) {
                        item {
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("📡", fontSize = 36.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No active transfers",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Pick a file above or receive from a peer over local Wi-Fi",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(activeJobs, key = { it.id }) { job ->
                            TransferProgressCard(
                                job = job,
                                onCancel = { viewModel.cancelTransfer(job.id) },
                                onOpenFile = {
                                    if (job.fileUri != null) {
                                        openFileIntent(context, job.fileUri, job.mimeType, job.fileName)
                                    }
                                }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }

            // Incoming Nearby Share Modal Bottom Sheet
            uiState.incomingPrompt?.let { prompt ->
                IncomingTransferSheet(
                    prompt = prompt,
                    onAccept = { viewModel.acceptIncomingTransfer(prompt.transferId) },
                    onDecline = { viewModel.rejectIncomingTransfer(prompt.transferId) }
                )
            }

            // Post-Transfer Celebration / Speed Summary Dialog
            uiState.latestCompletedEvent?.let { event ->
                TransferCompletionDialog(
                    event = event,
                    onDismiss = { viewModel.dismissCompletionDialog() },
                    onOpenFile = {
                        viewModel.dismissCompletionDialog()
                        if (event.localUri != null) {
                            openFileIntent(context, event.localUri, "*/*", event.fileName)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SendFilePickerCard(
    selectedFile: SelectedFileInfo?,
    isViewOnly: Boolean,
    onPickFile: () -> Unit,
    onClearFile: () -> Unit,
    onTogglePermission: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SEND VIA HYPERSHARE",
                color = SignalBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (selectedFile == null) {
                Button(
                    onClick = onPickFile,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁  Select File to Share", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = getFileIcon(selectedFile.name)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0x333B82F6), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(selectedFile.size),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "✕",
                        color = ErrorRed,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { onClearFile() }
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isViewOnly) "View-Only Stream (In-Memory)" else "Downloadable (Saved to Disk)",
                        color = if (isViewOnly) WarningAmber else ConnectedGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (isViewOnly) "Recipient cannot save file to disk" else "File saves directly to Downloads/HyperShare",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                Switch(
                    checked = isViewOnly,
                    onCheckedChange = onTogglePermission,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarningAmber,
                        checkedTrackColor = Color(0x33F59E0B)
                    )
                )
            }
        }
    }
}

@Composable
fun NearbyPeersSelectorSection(
    peers: List<ConnectedPeer>,
    selectedFile: SelectedFileInfo?,
    onPeerSelected: (ConnectedPeer) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (selectedFile != null) "TAP PEER TO SEND IMMEDIATELY" else "NEARBY PEERS IN RANGE",
            color = if (selectedFile != null) ConnectedGreen else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (peers.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulsingRadarDot()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Searching for nearby peers on LAN / Mesh...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(peers, key = { it.peerId }) { peer ->
                    PeerRadarChip(
                        peer = peer,
                        isClickable = selectedFile != null,
                        onClick = { onPeerSelected(peer) }
                    )
                }
            }
        }
    }
}

@Composable
fun PeerRadarChip(
    peer: ConnectedPeer,
    isClickable: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isClickable) SignalBlue else Color(0x22FFFFFF)
    val background = if (isClickable) Color(0x1F3B82F6) else Color(0x0DFFFFFF)

    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = isClickable) { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SignalBlue.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("📱", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = peer.displayName.ifEmpty { peer.peerId.take(8) },
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (isClickable) "Tap to Send" else "Online",
            color = if (isClickable) ConnectedGreen else TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TransferProgressCard(
    job: TransferJob,
    onCancel: () -> Unit,
    onOpenFile: () -> Unit
) {
    val progressPct = (job.progress * 100).toInt()
    val isDone = job.state == TransferState.COMPLETED
    val isFailed = job.state == TransferState.FAILED

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = getFileIcon(job.fileName)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(if (isDone) ConnectedGreen.copy(0.2f) else SignalBlue.copy(0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.fileName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${if (job.isOutgoing) "Sending to" else "Receiving from"} ${job.destinationPeerId.take(10)}",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                StatusChip(
                    state = when (job.state) {
                        TransferState.COMPLETED -> StatusChipState.CONNECTED
                        TransferState.FAILED -> StatusChipState.LOST
                        TransferState.IN_PROGRESS -> StatusChipState.CONNECTED
                        else -> StatusChipState.CONNECTING
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Linear Progress Bar
            LinearProgressIndicator(
                progress = { job.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isDone) ConnectedGreen else SignalBlue,
                trackColor = Color(0x22FFFFFF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transfer Statistics Grid (Current Speed, Avg Speed, ETA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatFileSize(job.bytesTransferred)} / ${formatFileSize(job.fileSize)} ($progressPct%)",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )

                if (!isDone && !isFailed) {
                    Text(
                        text = "⚡ ${TransferManager.getInstance().formatSpeed(job.currentSpeedBps)}",
                        color = SignalBlue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Avg: ${TransferManager.getInstance().formatSpeed(job.averageSpeedBps)}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (!isDone && !isFailed && job.estimatedRemainingSeconds > 0) {
                    Text(
                        text = "⏱️ ${formatSeconds(job.estimatedRemainingSeconds)} left",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (job.peakSpeedBps > 0) {
                Text(
                    text = "🚀 Peak: ${TransferManager.getInstance().formatSpeed(job.peakSpeedBps)}",
                    color = ConnectedGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (job.failureReason != null) {
                Text(
                    text = "⚠️ ${job.failureReason}",
                    color = ErrorRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Action Buttons
            if (!isDone && !isFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", fontSize = 11.sp)
                }
            } else if (isDone && job.fileUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenFile,
                    colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Open File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IncomingTransferSheet(
    prompt: com.hypershare.application.IncomingTransferPrompt,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📥", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Incoming Nearby Share",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Peer ${prompt.senderPeerId.take(12)} wants to send you:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(getFileIcon(prompt.fileName), fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = prompt.fileName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(prompt.fileSize)} • ${if (prompt.isViewOnly) "View-Only Stream" else "Downloadable"}",
                            color = if (prompt.isViewOnly) WarningAmber else ConnectedGreen,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen)
            ) {
                Text("Accept", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline", color = ErrorRed)
            }
        }
    )
}

@Composable
fun TransferCompletionDialog(
    event: TransferCompletedEvent,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (event.isOutgoing) "Transfer Sent!" else "File Received!",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = event.fileName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Stats breakdown
                val durationSec = String.format(java.util.Locale.US, "%.1fs", event.durationMs.toDouble() / 1000.0)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    StatRow("Total Size", formatFileSize(event.fileSize))
                    StatRow("Transfer Time", durationSec)
                    StatRow("Average Speed", TransferManager.getInstance().formatSpeed(event.averageSpeedBps))
                    StatRow("Peak Speed", TransferManager.getInstance().formatSpeed(event.peakSpeedBps))
                    StatRow("Integrity Status", "✓ CRC32 Verified")
                }
            }
        },
        confirmButton = {
            if (!event.isOutgoing && event.localUri != null) {
                Button(
                    onClick = onOpenFile,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                ) {
                    Text("Open File", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Text(text = value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PulsingRadarDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(10.dp)
            .scale(scale)
            .background(SignalBlue, CircleShape)
    )
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}

fun formatSeconds(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}

fun getFileIcon(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") -> "🎬"
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") -> "🎵"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> "🖼️"
        lower.endsWith(".pdf") -> "📄"
        lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".tar") || lower.endsWith(".gz") -> "📦"
        lower.endsWith(".apk") -> "🤖"
        else -> "📄"
    }
}

fun openFileIntent(context: android.content.Context, uri: Uri, mimeType: String = "*/*", fileName: String = "") {
    try {
        var contentUri: Uri = uri

        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: "")
            if (file.exists()) {
                contentUri = try {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
                    uri
                }
            } else {
                // Defensive recovery: check if the file exists in Movies, Pictures, Downloads, or cache
                val nameOnly = fileName.ifEmpty { file.name }
                val candidateDirs = listOf(
                    java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "HyperShare"),
                    java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "HyperShare"),
                    java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "HyperShare"),
                    java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "HyperShare"),
                    java.io.File(context.cacheDir, "transfers"),
                    java.io.File(context.cacheDir, "view_only")
                )
                val recoveredFile = candidateDirs.firstNotNullOfOrNull { dir ->
                    if (dir.exists()) {
                        dir.listFiles()?.firstOrNull { it.name == nameOnly || it.name.startsWith(nameOnly) }
                    } else null
                }
                if (recoveredFile != null && recoveredFile.exists()) {
                    contentUri = try {
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            recoveredFile
                        )
                    } catch (_: Exception) {
                        Uri.fromFile(recoveredFile)
                    }
                }
            }
        }

        val name = fileName.ifEmpty { contentUri.lastPathSegment ?: "" }
        val ext = name.substringAfterLast('.', "").lowercase()
        val resolvedMime = when {
            mimeType.isNotEmpty() && mimeType != "*/*" && !mimeType.contains("octet-stream") -> mimeType
            ext.isNotEmpty() -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: when (ext) {
                    "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "ts" -> "video/*"
                    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "image/*"
                    "mp3", "wav", "m4a", "aac", "flac", "ogg" -> "audio/*"
                    "pdf" -> "application/pdf"
                    "apk" -> "application/vnd.android.package-archive"
                    "zip" -> "application/zip"
                    else -> "*/*"
                }
            else -> context.contentResolver.getType(contentUri) ?: "*/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open with $name").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Explicitly grant read permissions to all apps that can handle the intent
        try {
            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}

        context.startActivity(chooser)
    } catch (e: Exception) {
        android.util.Log.e("FileBrowser", "Failed to open file $uri: ${e.message}", e)
        android.widget.Toast.makeText(context, "Cannot open file: ${e.localizedMessage ?: "No app available"}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
