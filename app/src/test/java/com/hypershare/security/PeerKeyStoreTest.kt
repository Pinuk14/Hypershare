package com.hypershare.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerKeyStoreTest {

    @Test
    fun storeAndGetPeerKey_retrievesStoredKey() {
        val keyStore = PeerKeyStore()
        val peerId = "peer-alpha-123"
        val mockKey = ByteArray(32) { it.toByte() }

        assertFalse(keyStore.hasPeerKey(peerId))
        keyStore.storePeerKey(peerId, mockKey)

        assertTrue(keyStore.hasPeerKey(peerId))
        assertArrayEquals(mockKey, keyStore.getPeerKey(peerId))
    }

    @Test
    fun removePeerKey_removesStoredKey() {
        val keyStore = PeerKeyStore()
        val peerId = "peer-beta-456"
        val mockKey = ByteArray(32) { (it + 5).toByte() }

        keyStore.storePeerKey(peerId, mockKey)
        assertTrue(keyStore.hasPeerKey(peerId))

        keyStore.removePeerKey(peerId)
        assertFalse(keyStore.hasPeerKey(peerId))
        assertNull(keyStore.getPeerKey(peerId))
    }
}
