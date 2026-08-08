package com.hypershare

import android.Manifest
import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        handleNotificationIntent(intent)

        // Request WiFi & Nearby Device Runtime Permissions
        requestNetworkPermissions()

        // Start background discovery & network services
        try {
            startForegroundService(Intent(this, DiscoveryService::class.java))
            startForegroundService(Intent(this, MeshNetworkService::class.java))
        } catch (e: Exception) {
            try {
                startService(Intent(this, DiscoveryService::class.java))
                startService(Intent(this, MeshNetworkService::class.java))
            } catch (e2: Exception) { }
        }

        setContent {
            val targetPeerId by targetPeerIdFlow.collectAsState()

            HyperShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HyperShareNavGraph(
                        targetPeerId = targetPeerId,
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

    private fun handleNotificationIntent(intent: Intent?) {
        val peerId = intent?.getStringExtra("OPEN_PEER_ID")
        if (!peerId.isNullOrEmpty()) {
            _targetPeerIdFlow.value = peerId
            intent.removeExtra("OPEN_PEER_ID")
        }
    }

    private fun requestNetworkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }
}
