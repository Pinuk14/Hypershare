package com.hypershare.application

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeveloperSettingsManager {
    private const val PREFS_NAME = "hypershare_dev_settings"
    private const val KEY_DEV_MODE = "developer_mode"

    private val _isDeveloperMode = MutableStateFlow(false)
    val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isDeveloperMode.value = prefs.getBoolean(KEY_DEV_MODE, false)
    }

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEV_MODE, enabled)
            .apply()
        _isDeveloperMode.value = enabled
    }
}
