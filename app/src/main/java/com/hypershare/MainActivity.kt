package com.hypershare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hypershare.service.DiscoveryService
import com.hypershare.service.MeshNetworkService
import com.hypershare.ui.navigation.HyperShareNavGraph
import com.hypershare.ui.theme.HyperShareTheme

class MainActivity : ComponentActivity() {

    private val _targetPeerIdFlow = MutableStateFlow<String?>(null)
    val targetPeerIdFlow = _targetPeerIdFlow.asStateFlow()
    private val _networkPermissionsGranted = MutableStateFlow(false)
    private var networkServicesStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        handleNotificationIntent(intent)

        if (hasRequiredNetworkPermissions()) {
            _networkPermissionsGranted.value = true
            startNetworkServices()
        } else {
            requestNetworkPermissions()
        }

        setContent {
            val targetPeerId by targetPeerIdFlow.collectAsState()
            val networkPermissionsGranted by _networkPermissionsGranted.collectAsState()

            HyperShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HyperShareNavGraph(
                        targetPeerId = targetPeerId,
                        networkPermissionsGranted = networkPermissionsGranted,
                        onPeerNavigated = { _targetPeerIdFlow.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NETWORK_PERMISSION_REQUEST_CODE && hasRequiredNetworkPermissions()) {
            _networkPermissionsGranted.value = true
            startNetworkServices()
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val peerId = intent?.getStringExtra("OPEN_PEER_ID")
        if (!peerId.isNullOrEmpty()) {
            _targetPeerIdFlow.value = peerId
            intent.removeExtra("OPEN_PEER_ID")
        }
    }

    private fun requestNetworkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, NETWORK_PERMISSION_REQUEST_CODE)
    }

    private fun hasRequiredNetworkPermissions(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startNetworkServices() {
        if (networkServicesStarted) return
        networkServicesStarted = true
        try {
            startForegroundService(Intent(this, DiscoveryService::class.java))
            startForegroundService(Intent(this, MeshNetworkService::class.java))
        } catch (e: Exception) {
            try {
                startService(Intent(this, DiscoveryService::class.java))
                startService(Intent(this, MeshNetworkService::class.java))
            } catch (e2: Exception) {
                networkServicesStarted = false
            }
        }
    }

    companion object {
        private const val NETWORK_PERMISSION_REQUEST_CODE = 101
    }
}
