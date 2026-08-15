package com.hypershare.model

import com.hypershare.identity.IdentityManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.Base64

@Serializable
data class GroupInviteCard(
    @SerialName("gid") val groupId: String,
    @SerialName("gn") val groupName: String,
    @SerialName("aid") val adminUserId: String,
    @SerialName("an") val adminDisplayName: String,
    @SerialName("apk") val adminPublicKeyB64: String,
    @SerialName("mc") val memberCount: Int,
    @SerialName("gt") val groupType: String,
    @SerialName("ca") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("ea") val expiresAt: Long = System.currentTimeMillis() + 120_000L,
    @SerialName("sig") val signatureB64: String = ""
) {
    val adminPublicKey: ByteArray
        get() = try { Base64.getDecoder().decode(adminPublicKeyB64) } catch (_: Exception) { ByteArray(0) }

    val signature: ByteArray
        get() = try { Base64.getDecoder().decode(signatureB64) } catch (_: Exception) { ByteArray(0) }

    /**
     * Constructs a deterministic byte payload of all fields except signature for Ed25519 signing & verification.
     */
    fun allFieldsExceptSignature(): ByteArray {
        val pubKeyBytes = adminPublicKey
        val gIdBytes = groupId.toByteArray(Charsets.UTF_8)
        val gNameBytes = groupName.toByteArray(Charsets.UTF_8)
        val adminIdBytes = adminUserId.toByteArray(Charsets.UTF_8)
        val adminNameBytes = adminDisplayName.toByteArray(Charsets.UTF_8)
        val gTypeBytes = groupType.toByteArray(Charsets.UTF_8)

        val capacity = 4 + gIdBytes.size +
                4 + gNameBytes.size +
                4 + adminIdBytes.size +
                4 + adminNameBytes.size +
                4 + pubKeyBytes.size +
                4 +
                4 + gTypeBytes.size +
                8 +
                8

        val buffer = ByteBuffer.allocate(capacity)
        buffer.putInt(gIdBytes.size)
        buffer.put(gIdBytes)
        buffer.putInt(gNameBytes.size)
        buffer.put(gNameBytes)
        buffer.putInt(adminIdBytes.size)
        buffer.put(adminIdBytes)
        buffer.putInt(adminNameBytes.size)
        buffer.put(adminNameBytes)
        buffer.putInt(pubKeyBytes.size)
        buffer.put(pubKeyBytes)
        buffer.putInt(memberCount)
        buffer.putInt(gTypeBytes.size)
        buffer.put(gTypeBytes)
        buffer.putLong(createdAt)
        buffer.putLong(expiresAt)

        return buffer.array()
    }

    /**
     * Verifies the Ed25519 signature of this invite card using [adminPublicKey].
     */
    fun verifyCardSignature(): Boolean {
        val pubKey = adminPublicKey
        val sig = signature
        if (sig.isEmpty() || pubKey.isEmpty()) return false
        val payload = allFieldsExceptSignature()
        return IdentityManager.verifySignature(pubKey, payload, sig)
    }

    /**
     * Compact JSON serialization for QR code matrix.
     */
    fun toJson(): String {
        return jsonSerializer.encodeToString(this)
    }

    companion object {
        private val jsonSerializer = Json { ignoreUnknownKeys = true }

        /**
         * Creates and signs a new GroupInviteCard using the admin's [IdentityManager].
         */
        fun createSignedInvite(
            identityManager: IdentityManager,
            groupId: String,
            groupName: String,
            adminDisplayName: String,
            memberCount: Int,
            groupType: GroupType,
            validityDurationMs: Long = 120_000L
        ): GroupInviteCard {
            val adminUserId = identityManager.getUserId()
            val pubKeyBytes = identityManager.getPublicKey()
            val pubKeyB64 = Base64.getEncoder().encodeToString(pubKeyBytes)
            val now = System.currentTimeMillis()

            val unsignedCard = GroupInviteCard(
                groupId = groupId,
                groupName = groupName,
                adminUserId = adminUserId,
                adminDisplayName = adminDisplayName,
                adminPublicKeyB64 = pubKeyB64,
                memberCount = memberCount,
                groupType = groupType.name,
                createdAt = now,
                expiresAt = now + validityDurationMs,
                signatureB64 = ""
            )

            val sigBytes = identityManager.signData(unsignedCard.allFieldsExceptSignature())
            val sigB64 = Base64.getEncoder().encodeToString(sigBytes)
            return unsignedCard.copy(signatureB64 = sigB64)
        }

        /**
         * Deserializes a GroupInviteCard from raw JSON string payload.
         */
        fun fromJson(jsonStr: String): GroupInviteCard? {
            return try {
                jsonSerializer.decodeFromString<GroupInviteCard>(jsonStr)
            } catch (_: Exception) {
                null
            }
        }
    }
}
