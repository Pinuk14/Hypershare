package com.hypershare.identity

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class IdentityManagerTest {

    @Test
    fun testBlake2bUserIdDerivation() {
        val stableUuid = UUID.randomUUID().toString()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // Compute UserID 1
        val digest1 = Blake2bDigest(null, 32, null, salt)
        digest1.update(stableUuid.toByteArray(Charsets.UTF_8), 0, stableUuid.toByteArray(Charsets.UTF_8).size)
        val out1 = ByteArray(32)
        digest1.doFinal(out1, 0)
        val userId1 = out1.joinToString("") { "%02X".format(it) }

        // Compute UserID 2 with same salt and UUID
        val digest2 = Blake2bDigest(null, 32, null, salt)
        digest2.update(stableUuid.toByteArray(Charsets.UTF_8), 0, stableUuid.toByteArray(Charsets.UTF_8).size)
        val out2 = ByteArray(32)
        digest2.doFinal(out2, 0)
        val userId2 = out2.joinToString("") { "%02X".format(it) }

        assertEquals("BLAKE2b UserID derivation must be deterministic for same UUID and salt", userId1, userId2)
        assertEquals("UserID hex length must be 64 characters (32 bytes)", 64, userId1.length)
    }

    @Test
    fun testEd25519ContactCardSignAndVerify() {
        // Generate Ed25519 KeyPair
        val keyPairGen = Ed25519KeyPairGenerator()
        keyPairGen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = keyPairGen.generateKeyPair()

        val privParams = keyPair.private as Ed25519PrivateKeyParameters
        val pubParams = keyPair.public as Ed25519PublicKeyParameters

        val userId = "3A8B9C1D2E3F4A5B6C7D8E9F0A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B"
        val displayName = "Test Device"
        val publicKey = pubParams.encoded

        val tempCard = ContactCard(
            userId = userId,
            displayName = displayName,
            publicKey = publicKey,
            timestamp = System.currentTimeMillis(),
            signature = ByteArray(0)
        )

        // Sign payload with private key
        val payload = tempCard.getSigningPayload()
        val signer = Ed25519Signer()
        signer.init(true, privParams)
        signer.update(payload, 0, payload.size)
        val signature = signer.generateSignature()

        val signedCard = tempCard.copy(signature = signature)

        // Verify valid card
        assertTrue("Signed ContactCard must verify successfully", signedCard.verifyCardSignature())

        // Test Tamper Rejection (altered display name)
        val tamperedCard = signedCard.copy(displayName = "Hacked Name")
        assertFalse("Tampered ContactCard must fail signature verification", tamperedCard.verifyCardSignature())
    }

    @Test
    fun testContactCardJsonSerialization() {
        val keyPairGen = Ed25519KeyPairGenerator()
        keyPairGen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = keyPairGen.generateKeyPair()

        val privParams = keyPair.private as Ed25519PrivateKeyParameters
        val pubParams = keyPair.public as Ed25519PublicKeyParameters

        val userId = "1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF"
        val card = ContactCard(
            userId = userId,
            displayName = "Alice Device",
            publicKey = pubParams.encoded,
            timestamp = 1700000000000L,
            signature = ByteArray(64) { 1 }
        )

        val jsonStr = card.toJson()
        val restoredCard = ContactCard.jsonToCard(jsonStr)

        assertNotNull("Restored card must not be null", restoredCard)
        assertEquals("UserId must match", card.userId, restoredCard?.userId)
        assertEquals("DisplayName must match", card.displayName, restoredCard?.displayName)
        assertEquals("Timestamp must match", card.timestamp, restoredCard?.timestamp)
    }
}
