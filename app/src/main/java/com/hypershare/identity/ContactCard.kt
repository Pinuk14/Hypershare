package com.hypershare.identity

import java.nio.ByteBuffer

data class ContactCard(
    val userId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: ByteArray
) {
    fun getSigningPayload(): ByteArray {
        val userIdBytes = userId.toByteArray(Charsets.UTF_8)
        val nameBytes = displayName.toByteArray(Charsets.UTF_8)
        
        val buffer = ByteBuffer.allocate(4 + userIdBytes.size + 4 + nameBytes.size + 4 + publicKey.size + 8)
        buffer.putInt(userIdBytes.size)
        buffer.put(userIdBytes)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.putInt(publicKey.size)
        buffer.put(publicKey)
        buffer.putLong(timestamp)
        return buffer.array()
    }

    fun verifyCardSignature(): Boolean {
        if (signature.isEmpty() || publicKey.isEmpty()) return false
        val payload = getSigningPayload()
        return IdentityManager.verifySignature(publicKey, payload, signature)
    }

    fun toJson(): String {
        val pubB64 = encodeBase64(publicKey)
        val sigB64 = encodeBase64(signature)
        val escapedName = displayName.replace("\"", "\\\"")
        return """{"userId":"$userId","displayName":"$escapedName","publicKey":"$pubB64","timestamp":$timestamp,"signature":"$sigB64"}"""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactCard

        if (userId != other.userId) return false
        if (displayName != other.displayName) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (timestamp != other.timestamp) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    companion object {
        fun createSignedCard(identityManager: IdentityManager, displayName: String): ContactCard {
            val userId = identityManager.getUserId()
            val publicKey = identityManager.getPublicKey()
            val timestamp = System.currentTimeMillis()
            
            val tempCard = ContactCard(
                userId = userId,
                displayName = displayName,
                publicKey = publicKey,
                timestamp = timestamp,
                signature = ByteArray(0)
            )
            
            val signature = identityManager.signData(tempCard.getSigningPayload())
            return tempCard.copy(signature = signature)
        }

        fun jsonToCard(jsonStr: String): ContactCard? {
            return try {
                val userId = extractJsonField(jsonStr, "userId") ?: return null
                val displayName = extractJsonField(jsonStr, "displayName") ?: return null
                val pubB64 = extractJsonField(jsonStr, "publicKey") ?: return null
                val timestampStr = extractJsonRawField(jsonStr, "timestamp") ?: return null
                val sigB64 = extractJsonField(jsonStr, "signature") ?: return null

                ContactCard(
                    userId = userId,
                    displayName = displayName,
                    publicKey = decodeBase64(pubB64),
                    timestamp = timestampStr.toLong(),
                    signature = decodeBase64(sigB64)
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun encodeBase64(bytes: ByteArray): String {
            return try {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (_: Throwable) {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }

        private fun decodeBase64(str: String): ByteArray {
            return try {
                android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
            } catch (_: Throwable) {
                java.util.Base64.getDecoder().decode(str)
            }
        }

        private fun extractJsonField(json: String, key: String): String? {
            val regex = """"$key"\s*:\s*"([^"]*)"""".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }

        private fun extractJsonRawField(json: String, key: String): String? {
            val regex = """"$key"\s*:\s*([0-9]+)""".toRegex()
            return regex.find(json)?.groupValues?.get(1)
        }
    }
}

