package com.hypershare.application

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.hypershare.identity.IdentityManager

class UserIdentityManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val identityManager = IdentityManager.getInstance(appContext)

    init {
        ensureIdentityInitialized()
    }

    private fun ensureIdentityInitialized() {
        if (!prefs.contains(KEY_USERNAME)) {
            val deviceModelName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
            prefs.edit().putString(KEY_USERNAME, deviceModelName).apply()
        }
    }

    fun getPeerId(): String {
        return identityManager.getUserId()
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
        return identityManager.getPublicKeyFingerprint()
    }

    companion object {
        private const val PREFS_NAME = "hypershare_identity_prefs"
        private const val KEY_USERNAME = "username"

        @Volatile
        private var instance: UserIdentityManager? = null

        fun getInstance(context: Context): UserIdentityManager {
            return instance ?: synchronized(this) {
                instance ?: UserIdentityManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

