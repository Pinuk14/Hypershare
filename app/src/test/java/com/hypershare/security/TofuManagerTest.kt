package com.hypershare.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TofuManagerTest {

    @Test
    fun verifyOrTrustPeer_firstUse_trustsAndPinsKey() {
        val keyStore = PeerKeyStore()
        val tofuManager = TofuManager(keyStore)

        val peerId = "node-gamma"
        val originalKey = ByteArray(32) { (it * 2).toByte() }

        // Trust On First Use (TOFU)
        val isFirstConnectTrusted = tofuManager.verifyOrTrustPeer(peerId, originalKey)
        assertTrue("First connect must trust and pin key", isFirstConnectTrusted)
        assertTrue(keyStore.hasPeerKey(peerId))

        // Subsequent connect with same key
        val isSecondConnectTrusted = tofuManager.verifyOrTrustPeer(peerId, originalKey)
        assertTrue("Subsequent connect with matching key must pass verification", isSecondConnectTrusted)
    }

    @Test
    fun verifyOrTrustPeer_subsequentConnectWithAlteredKey_rejectsVerification() {
        val keyStore = PeerKeyStore()
        val tofuManager = TofuManager(keyStore)

        val peerId = "node-delta"
        val originalKey = ByteArray(32) { 0x01 }
        val alteredKey = ByteArray(32) { 0x02 }

        // First use
        tofuManager.verifyOrTrustPeer(peerId, originalKey)

        // Subsequent connect with altered key (MITM / changed identity)
        val isVerificationPassed = tofuManager.verifyOrTrustPeer(peerId, alteredKey)
        assertFalse("Key mismatch must fail TOFU verification", isVerificationPassed)
    }
}
