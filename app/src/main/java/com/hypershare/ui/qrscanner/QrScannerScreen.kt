package com.hypershare.ui.qrscanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.hypershare.ui.components.BottomNavBar
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.ErrorRed
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    viewModel: QrScannerViewModel,
    onBackClick: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Gallery Photo Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.decodeGalleryPhoto(uri)
        }
    }

    var showManualPasteDialog by remember { mutableStateOf(false) }
    var manualPayloadText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = BackgroundBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomNavBar(
                selectedTab = NavTab.PEERS,
                onTabSelected = { tab ->
                    when (tab) {
                        NavTab.HOME -> onBackClick()
                        NavTab.ACCOUNT -> onBackClick()
                        NavTab.SETTINGS -> onBackClick()
                        NavTab.PEERS -> onBackClick()
                    }
                },
                onOpenAppSettings = onBackClick,
                onOpenAccountSettings = onBackClick,
                onOpenHome = onBackClick
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview View
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        val executor = Executors.newSingleThreadExecutor()

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            val reader = MultiFormatReader()
                            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                if (!uiState.isProcessing && uiState.scannedCard == null) {
                                    val buffer = imageProxy.planes[0].buffer
                                    val data = ByteArray(buffer.remaining())
                                    buffer.get(data)
                                    val width = imageProxy.width
                                    val height = imageProxy.height

                                    try {
                                        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
                                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                                        val result = reader.decode(binaryBitmap)
                                        result?.text?.let { raw ->
                                            viewModel.onPayloadScanned(raw)
                                        }
                                    } catch (_: Exception) {
                                    } finally {
                                        imageProxy.close()
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷 Camera Permission Required", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                        ) {
                            Text("Grant Permission", color = TextPrimary)
                        }
                    }
                }
            }

            // Scanning Reticle Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = 250.dp.toPx()
                val left = (size.width - boxSize) / 2f
                val top = (size.height - boxSize) / 2.2f

                drawRoundRect(
                    color = ConnectedGreen,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f))
                )
            }

            // Screen Controls Overlay
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(BackgroundBase)
                )

                // Top Header Banner
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
                            text = "SCAN CONTACT QR CODE",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Actions: Pick from Gallery & Manual Payload Entry
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    cornerRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pick Photo from Gallery Button
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Gallery QR", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Paste Payload Manual Entry Button
                        Button(
                            onClick = { showManualPasteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Paste Text", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Scanned Result Overlay Modal
            if (uiState.scanMessage != null) {
                val isValid = uiState.isSignatureValid == true
                AlertDialog(
                    onDismissRequest = { viewModel.resetScanState() },
                    title = {
                        Text(
                            text = if (isValid) "Mutual Contact Verified" else "QR Scan Notice",
                            color = if (isValid) ConnectedGreen else ErrorRed,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(text = uiState.scanMessage ?: "", color = TextPrimary, fontSize = 14.sp)
                            uiState.scannedCard?.let { card ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Peer ID: ${card.userId.take(16)}...",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Signature: ${if (isValid) "Ed25519 VERIFIED" else "INVALID/TAMPERED"}",
                                    color = if (isValid) ConnectedGreen else ErrorRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (isValid && uiState.scannedCard != null) {
                            Button(
                                onClick = {
                                    val peerId = uiState.scannedCard!!.userId
                                    viewModel.resetScanState()
                                    onOpenChat(peerId)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ConnectedGreen)
                            ) {
                                Text("Start Encrypted Chat", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TextButton(onClick = { viewModel.resetScanState() }) {
                                Text("OK", color = SignalBlue)
                            }
                        }
                    },
                    dismissButton = {
                        if (isValid) {
                            TextButton(onClick = { viewModel.resetScanState() }) {
                                Text("Dismiss", color = TextSecondary)
                            }
                        }
                    },
                    containerColor = BackgroundBase
                )
            }

            // Manual Text Paste Fallback Dialog
            if (showManualPasteDialog) {
                AlertDialog(
                    onDismissRequest = { showManualPasteDialog = false },
                    title = { Text("Paste QR Payload JSON", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = manualPayloadText,
                            onValueChange = { manualPayloadText = it },
                            label = { Text("JSON Payload", color = TextSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = SignalBlue,
                                unfocusedBorderColor = TextSecondary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showManualPasteDialog = false
                                viewModel.onPayloadScanned(manualPayloadText)
                                manualPayloadText = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                        ) {
                            Text("Verify Payload", color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualPasteDialog = false }) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = BackgroundBase
                )
            }
        }
    }
}
