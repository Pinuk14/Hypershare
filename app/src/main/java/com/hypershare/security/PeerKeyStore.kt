package com.hypershare.security

import java.util.concurrent.ConcurrentHashMap

class PeerKeyStore {
    private val keyStore = ConcurrentHashMap<String, ByteArray>()

    fun storePeerKey(peerId: String, publicKey: ByteArray) {
        keyStore[peerId] = publicKey
    }

    fun getPeerKey(peerId: String): ByteArray? {
        return keyStore[peerId]
    }

    fun hasPeerKey(peerId: String): Boolean {
        return keyStore.containsKey(peerId)
    }

    fun removePeerKey(peerId: String) {
        keyStore.remove(peerId)
    }
}
