package com.hypershare.security

class TofuManager(private val peerKeyStore: PeerKeyStore) {

    fun verifyOrTrustPeer(peerId: String, receivedPublicKeyBytes: ByteArray): Boolean {
        val existingKey = peerKeyStore.getPeerKey(peerId)
        return if (existingKey == null) {
            // Trust On First Use (TOFU)
            peerKeyStore.storePeerKey(peerId, receivedPublicKeyBytes)
            true
        } else {
            // Compare pinned public key
            existingKey.contentEquals(receivedPublicKeyBytes)
        }
    }
}
