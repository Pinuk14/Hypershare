package com.hypershare.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.KeyAgreement

class EphemeralKeyPair private constructor(
    val keyPair: KeyPair
) {
    val publicKeyBytes: ByteArray
        get() = keyPair.public.encoded

    fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val keySpec = java.security.spec.X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPublicKey = keyFactory.generatePublic(keySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    companion object {
        fun generate(): EphemeralKeyPair {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256, SecureRandom())
            val kp = kpg.generateKeyPair()
            return EphemeralKeyPair(kp)
        }
    }
}
