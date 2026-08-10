package com.hypershare.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.UUID

class IdentityManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val stableDeviceUUID: String
    private val saltBytes: ByteArray
    private val userId: String
    private val privateKeyBytes: ByteArray
    private val publicKeyBytes: ByteArray

    init {
        // 1. Initialize or load Stable Device UUID
        val existingUUID = prefs.getString(KEY_STABLE_UUID, null)
        if (existingUUID != null) {
            stableDeviceUUID = existingUUID
        } else {
            stableDeviceUUID = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_STABLE_UUID, stableDeviceUUID).apply()
        }

        // 2. Initialize or load 16-byte Salt
        val existingSaltBase64 = prefs.getString(KEY_SALT_BASE64, null)
        if (existingSaltBase64 != null) {
            saltBytes = Base64.decode(existingSaltBase64, Base64.NO_WRAP)
        } else {
            saltBytes = ByteArray(16)
            SecureRandom().nextBytes(saltBytes)
            prefs.edit().putString(KEY_SALT_BASE64, Base64.encodeToString(saltBytes, Base64.NO_WRAP)).apply()
        }

        // 3. Derive UserID = BLAKE2b-256(stableDeviceUUID + salt)
        userId = computeBlake2bUserIdHex(stableDeviceUUID, saltBytes)

        // 4. Initialize or load Ed25519 KeyPair
        val existingPrivBase64 = prefs.getString(KEY_ED25519_PRIV_BASE64, null)
        val existingPubBase64 = prefs.getString(KEY_ED25519_PUB_BASE64, null)

        if (existingPrivBase64 != null && existingPubBase64 != null) {
            privateKeyBytes = Base64.decode(existingPrivBase64, Base64.NO_WRAP)
            publicKeyBytes = Base64.decode(existingPubBase64, Base64.NO_WRAP)
        } else {
            val keyPairGen = Ed25519KeyPairGenerator()
            keyPairGen.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keyPair = keyPairGen.generateKeyPair()

            val privParams = keyPair.private as Ed25519PrivateKeyParameters
            val pubParams = keyPair.public as Ed25519PublicKeyParameters

            privateKeyBytes = privParams.encoded
            publicKeyBytes = pubParams.encoded

            prefs.edit()
                .putString(KEY_ED25519_PRIV_BASE64, Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP))
                .putString(KEY_ED25519_PUB_BASE64, Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP))
                .apply()
        }
    }

    fun getUserId(): String = userId

    fun getStableDeviceUUID(): String = stableDeviceUUID

    fun getPublicKey(): ByteArray = publicKeyBytes.clone()

    fun getPublicKeyFingerprint(): String {
        val hash = computeBlake2b256(publicKeyBytes)
        val hex = hash.joinToString("") { "%02X".format(it) }
        return "ED25519:${hex.take(16)}"
    }

    fun signData(data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        signer.init(true, privateKeyParams)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    companion object {
        private const val PREFS_NAME = "hypershare_identity_v2_prefs"
        private const val KEY_STABLE_UUID = "stable_device_uuid"
        private const val KEY_SALT_BASE64 = "identity_salt_base64"
        private const val KEY_ED25519_PRIV_BASE64 = "ed25519_priv_base64"
        private const val KEY_ED25519_PUB_BASE64 = "ed25519_pub_base64"

        @Volatile
        private var instance: IdentityManager? = null

        fun getInstance(context: Context): IdentityManager {
            return instance ?: synchronized(this) {
                instance ?: IdentityManager(context.applicationContext).also { instance = it }
            }
        }

        fun verifySignature(publicKeyBytes: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
            return try {
                val signer = Ed25519Signer()
                val publicKeyParams = Ed25519PublicKeyParameters(publicKeyBytes, 0)
                signer.init(false, publicKeyParams)
                signer.update(data, 0, data.size)
                signer.verifySignature(signature)
            } catch (e: Exception) {
                false
            }
        }

        private fun computeBlake2bUserIdBytes(uuidStr: String, salt: ByteArray): ByteArray {
            val digest = Blake2bDigest(null, 32, null, salt)
            val uuidBytes = uuidStr.toByteArray(Charsets.UTF_8)
            digest.update(uuidBytes, 0, uuidBytes.size)
            val out = ByteArray(32)
            digest.doFinal(out, 0)
            return out
        }

        private fun computeBlake2bUserIdHex(uuidStr: String, salt: ByteArray): String {
            val outBytes = computeBlake2bUserIdBytes(uuidStr, salt)
            return outBytes.joinToString("") { b -> "%02X".format(b) }
        }

        private fun computeBlake2b256(data: ByteArray): ByteArray {
            val digest = Blake2bDigest(256)
            digest.update(data, 0, data.size)
            val out = ByteArray(32)
            digest.doFinal(out, 0)
            return out
        }
    }
}
