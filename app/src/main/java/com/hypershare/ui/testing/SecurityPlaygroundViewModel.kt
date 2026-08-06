package com.hypershare.ui.testing

import androidx.lifecycle.ViewModel
import com.hypershare.protocol.PacketBuilder
import com.hypershare.protocol.PacketParser
import com.hypershare.security.EphemeralKeyPair
import com.hypershare.security.PeerKeyStore
import com.hypershare.security.SessionEncryptor
import com.hypershare.security.TofuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SecurityPlaygroundUiState(
    val peerAPublicKeyHex: String = "Not Generated",
    val peerBPublicKeyHex: String = "Not Generated",
    val derivedSecretAHex: String = "Not Derived",
    val derivedSecretBHex: String = "Not Derived",
    val secretsMatch: Boolean = false,
    val inputText: String = "HyperShare Secret Payload",
    val encryptedHex: String = "",
    val decryptedText: String = "",
    val packetSerializedHex: String = "",
    val packetParsedSummary: String = "",
    val tofuStatus: String = "Untested"
)

class SecurityPlaygroundViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityPlaygroundUiState())
    val uiState: StateFlow<SecurityPlaygroundUiState> = _uiState.asStateFlow()

    private var peerAKeyPair: EphemeralKeyPair? = null
    private var peerBKeyPair: EphemeralKeyPair? = null
    private var activeEncryptor: SessionEncryptor? = null
    private val keyStore = PeerKeyStore()
    private val tofuManager = TofuManager(keyStore)

    fun generateKeysAndDeriveSecret() {
        val a = EphemeralKeyPair.generate()
        val b = EphemeralKeyPair.generate()
        peerAKeyPair = a
        peerBKeyPair = b

        val secretA = a.computeSharedSecret(b.publicKeyBytes)
        val secretB = b.computeSharedSecret(a.publicKeyBytes)
        activeEncryptor = SessionEncryptor(secretA)

        val match = secretA.contentEquals(secretB)

        _uiState.value = _uiState.value.copy(
            peerAPublicKeyHex = a.publicKeyBytes.take(12).joinToString("") { "%02X".format(it) } + "...",
            peerBPublicKeyHex = b.publicKeyBytes.take(12).joinToString("") { "%02X".format(it) } + "...",
            derivedSecretAHex = secretA.take(8).joinToString("") { "%02X".format(it) } + "...",
            derivedSecretBHex = secretB.take(8).joinToString("") { "%02X".format(it) } + "...",
            secretsMatch = match
        )
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun testEncryption() {
        val encryptor = activeEncryptor ?: run {
            generateKeysAndDeriveSecret()
            activeEncryptor!!
        }

        val plaintextBytes = _uiState.value.inputText.toByteArray(Charsets.UTF_8)
        val ciphertext = encryptor.encrypt(plaintextBytes)
        val decryptedBytes = encryptor.decrypt(ciphertext)

        _uiState.value = _uiState.value.copy(
            encryptedHex = ciphertext.joinToString("") { "%02X".format(it) },
            decryptedText = String(decryptedBytes, Charsets.UTF_8)
        )
    }

    fun testPacketSerialization() {
        val packet = PacketBuilder.buildMessagePacket(
            sourcePeerId = "Peer-A",
            destPeerId = "Peer-B",
            seqNum = 42,
            text = _uiState.value.inputText
        )

        val serialized = PacketBuilder.serializePacket(packet)
        val parsed = PacketParser.parsePacket(serialized)

        _uiState.value = _uiState.value.copy(
            packetSerializedHex = serialized.take(24).joinToString("") { "%02X".format(it) } + "... (${serialized.size} bytes)",
            packetParsedSummary = "Type: ${parsed.header.type}, Seq: ${parsed.header.sequenceNumber}, Src: ${parsed.header.sourcePeerId} -> Dst: ${parsed.header.destinationPeerId}, Payload: \"${String(parsed.payload, Charsets.UTF_8)}\""
        )
    }

    fun testTofuVerification() {
        val peerId = "Test-Node-1"
        val mockKey = ByteArray(32) { 0xA5.toByte() }

        val firstConnect = tofuManager.verifyOrTrustPeer(peerId, mockKey)
        val secondConnect = tofuManager.verifyOrTrustPeer(peerId, mockKey)

        val alteredKey = ByteArray(32) { 0xFF.toByte() }
        val alteredConnect = tofuManager.verifyOrTrustPeer(peerId, alteredKey)

        val status = if (firstConnect && secondConnect && !alteredConnect) {
            "SUCCESS (1st Trust: PASS, 2nd Trust: PASS, Altered Key: REJECTED)"
        } else {
            "FAILED"
        }

        _uiState.value = _uiState.value.copy(tofuStatus = status)
    }
}
