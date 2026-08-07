package com.hypershare.application

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID

class UserIdentityManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureIdentityInitialized()
    }

    private fun ensureIdentityInitialized() {
        if (!prefs.contains(KEY_PEER_ID)) {
            val generatedPeerId = "HYPERSHARE-${Build.MODEL.uppercase().replace(" ", "_")}-${UUID.randomUUID().toString().take(4)}"
            prefs.edit().putString(KEY_PEER_ID, generatedPeerId).apply()
        }
        if (!prefs.contains(KEY_USERNAME)) {
            val deviceModelName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
            prefs.edit().putString(KEY_USERNAME, deviceModelName).apply()
        }
        if (!prefs.contains(KEY_KEY_FINGERPRINT)) {
            val generatedFingerprint = "EC:${UUID.randomUUID().toString().take(8).uppercase()}:${UUID.randomUUID().toString().take(8).uppercase()}"
            prefs.edit().putString(KEY_KEY_FINGERPRINT, generatedFingerprint).apply()
        }
    }

    fun getPeerId(): String {
        return prefs.getString(KEY_PEER_ID, "HYPERSHARE-NODE-DEFAULT")!!
    }

    fun getUsername(): String {
        return prefs.getString(KEY_USERNAME, "${Build.MANUFACTURER} ${Build.MODEL}")!!
    }

    fun setUsername(newUsername: String) {
        val trimmed = newUsername.trim()
        if (trimmed.isNotEmpty()) {
            prefs.edit().putString(KEY_USERNAME, trimmed).apply()
        }
    }

    fun getKeyFingerprint(): String {
        return prefs.getString(KEY_KEY_FINGERPRINT, "EC:3B:82:F6:14:B8:A6:22")!!
    }

    companion object {
        private const val PREFS_NAME = "hypershare_identity_prefs"
        private const val KEY_PEER_ID = "peer_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_KEY_FINGERPRINT = "key_fingerprint"

        @Volatile
        private var instance: UserIdentityManager? = null

        fun getInstance(context: Context): UserIdentityManager {
            return instance ?: synchronized(this) {
                instance ?: UserIdentityManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
