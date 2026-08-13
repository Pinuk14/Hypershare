package com.hypershare.ui.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypershare.ui.components.GlassCard
import com.hypershare.ui.components.StatusChip
import com.hypershare.ui.theme.BackgroundBase
import com.hypershare.ui.theme.ConnectedGreen
import com.hypershare.ui.theme.MeshTeal
import com.hypershare.ui.theme.SignalBlue
import com.hypershare.ui.theme.TextPrimary
import com.hypershare.ui.theme.TextSecondary

@Composable
fun SecurityPlaygroundScreen(
    viewModel: SecurityPlaygroundViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = BackgroundBase
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Crypto & Protocol Playground",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Phase 0 Security & Binary Protocol Interactive Test UI",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Section 1: ECDH Key Agreement
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. ECDH Curve25519 Shared Secret", color = SignalBlue, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Peer A PubKey: ${uiState.peerAPublicKeyHex}", color = TextSecondary, fontSize = 11.sp)
                    Text("Peer B PubKey: ${uiState.peerBPublicKeyHex}", color = TextSecondary, fontSize = 11.sp)
                    Text("Derived Secret A: ${uiState.derivedSecretAHex}", color = TextPrimary, fontSize = 11.sp)
                    Text("Derived Secret B: ${uiState.derivedSecretBHex}", color = TextPrimary, fontSize = 11.sp)
                    Text(
                        text = if (uiState.secretsMatch) "✓ ECDH MATCH SUCCESS" else "Tap below to run exchange",
                        color = if (uiState.secretsMatch) ConnectedGreen else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = { viewModel.generateKeysAndDeriveSecret() },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Generate & Compute ECDH")
                    }
                }
            }

            // Section 2: AES-256-GCM Encryption
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. AES-256-GCM Encryption / Decryption", color = MeshTeal, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.updateInputText(it) },
                        label = { Text("Plaintext Input") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { viewModel.testEncryption() },
                        colors = ButtonDefaults.buttonColors(containerColor = MeshTeal)
                    ) {
                        Text("Encrypt & Decrypt")
                    }
                    if (uiState.encryptedHex.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ciphertext + IV (Hex):", color = TextSecondary, fontSize = 11.sp)
                        Text(uiState.encryptedHex, color = TextPrimary, fontSize = 10.sp)
                        Text("Decrypted Output: ${uiState.decryptedText}", color = ConnectedGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Section 3: Binary Packet Serialization
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("3. Binary Packet Serialization (PacketBuilder)", color = SignalBlue, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { viewModel.testPacketSerialization() },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text("Serialize & Parse MSG Packet")
                    }
                    if (uiState.packetSerializedHex.isNotEmpty()) {
                        Text("Binary Packet (Hex): ${uiState.packetSerializedHex}", color = TextSecondary, fontSize = 11.sp)
                        Text(uiState.packetParsedSummary, color = ConnectedGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Section 4: TOFU Key Verification
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("4. TOFU Key Store Verification", color = MeshTeal, fontWeight = FontWeight.Bold)
                    Text("Status: ${uiState.tofuStatus}", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Button(
                        onClick = { viewModel.testTofuVerification() },
                        colors = ButtonDefaults.buttonColors(containerColor = MeshTeal)
                    ) {
                        Text("Run TOFU Test Flow")
                    }
                }
            }

            // Section 5: UI Status Chip Inspector
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("5. Theme Status Chips Inspector", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.hypershare.ui.components.StatusChip(state = com.hypershare.ui.components.StatusChipState.CONNECTED)
                        com.hypershare.ui.components.StatusChip(state = com.hypershare.ui.components.StatusChipState.RELAYING, hopCount = 2)
                        com.hypershare.ui.components.StatusChip(state = com.hypershare.ui.components.StatusChipState.CONNECTING)
                    }
                }
            }

            Button(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = com.hypershare.ui.theme.SurfaceCard)
            ) {
                Text("Back to Peers")
            }
        }
    }
}
