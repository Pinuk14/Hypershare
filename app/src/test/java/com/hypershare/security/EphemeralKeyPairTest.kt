package com.hypershare.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralKeyPairTest {

    @Test
    fun generate_createsValidECKeyPair() {
        val keyPair = EphemeralKeyPair.generate()
        assertNotNull(keyPair.keyPair)
        assertNotNull(keyPair.publicKeyBytes)
        assertTrue(keyPair.publicKeyBytes.isNotEmpty())
    }

    @Test
    fun computeSharedSecret_derivesMatchingSecretsForBothPeers() {
        val peerA = EphemeralKeyPair.generate()
        val peerB = EphemeralKeyPair.generate()

        val secretDerivedByA = peerA.computeSharedSecret(peerB.publicKeyBytes)
        val secretDerivedByB = peerB.computeSharedSecret(peerA.publicKeyBytes)

        assertNotNull(secretDerivedByA)
        assertNotNull(secretDerivedByB)
        assertTrue(secretDerivedByA.isNotEmpty())
        assertArrayEquals("Derived ECDH shared secrets must match exactly on both sides", secretDerivedByA, secretDerivedByB)
    }
}
