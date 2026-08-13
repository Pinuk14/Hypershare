package com.hypershare.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class AppSettingsUiState(
    val isDarkMode: Boolean = true,
    val networkPort: Int = 47200,
    val autoSwitchEmergencyMode: Boolean = true
)

class AppSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    fun toggleDarkMode(isDark: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = isDark)
    }

    fun toggleAutoSwitch(autoSwitch: Boolean) {
        _uiState.value = _uiState.value.copy(autoSwitchEmergencyMode = autoSwitch)
    }

    fun clearAllAppData(context: Context) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                activityManager.clearApplicationUserData()
            } else {
                performManualWipe(context)
            }
        } catch (e: Exception) {
            performManualWipe(context)
        }
    }

    private fun performManualWipe(context: Context) {
        try {
            // Delete SQLite Database
            context.deleteDatabase("hypershare_chat.db")

            // Clear SharedPreferences files
            val dataDir = context.applicationInfo.dataDir
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                sharedPrefsDir.listFiles()?.forEach { it.delete() }
            }

            // Clear Cache and Files directories
            context.cacheDir.deleteRecursively()
            context.filesDir.deleteRecursively()
        } catch (_: Exception) {
        } finally {
            // Kill the current process to ensure clean restart/re-creation of singletons
            Process.killProcess(Process.myPid())
            System.exit(0)
        }
    }
}

