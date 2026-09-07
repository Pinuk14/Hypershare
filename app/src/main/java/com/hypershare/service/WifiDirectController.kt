package com.hypershare.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns Android Wi-Fi Direct discovery and group formation for the P2P milestone.
 * Socket, routing, transfer, and encryption work intentionally remain outside this controller.
 */
data class WifiDirectConnectionState(
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerIpAddress: String? = null
)

class WifiDirectController(context: Context) {

    private val appContext = context.applicationContext
    private val wifiP2pManager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = wifiP2pManager.initialize(appContext, Looper.getMainLooper()) {
        Log.w(TAG, "Wi-Fi Direct channel disconnected")
        resetConnectionState()
    }

    private val _discoveredPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiP2pDevice>> = _discoveredPeers.asStateFlow()

    private val _connectionState = MutableStateFlow(WifiDirectConnectionState())
    val connectionState: StateFlow<WifiDirectConnectionState> = _connectionState.asStateFlow()

    private val _peerIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerIds: StateFlow<Map<String, String>> = _peerIds.asStateFlow()

    private var receiverRegistered = false
    private var selectedPeerAddress: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        startPeerDiscovery()
                    } else {
                        _discoveredPeers.value = emptyList()
                        resetConnectionState()
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        requestConnectionInfo()
                    } else {
                        resetConnectionState()
                    }
                }
            }
        }
    }

    /** Registers for P2P broadcasts and begins device discovery. */
    fun start() {
        if (!hasRequiredPermission()) {
            Log.w(TAG, "Wi-Fi Direct start ignored because the required runtime permission is not granted")
            return
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }
        startPeerDiscovery()
    }

    /** Stops active discovery and unregisters the dynamic Wi-Fi Direct receiver. */
    fun stop() {
        if (!receiverRegistered && !_connectionState.value.isDiscovering) return
        if (hasRequiredPermission()) {
            if (_connectionState.value.isConnecting) {
                wifiP2pManager.cancelConnect(channel, silentActionListener)
            }
            if (_connectionState.value.isConnected) {
                wifiP2pManager.removeGroup(channel, silentActionListener)
            }
            wifiP2pManager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = Unit
                override fun onFailure(reason: Int) = Unit
            })
        }
        if (receiverRegistered) {
            appContext.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        _discoveredPeers.value = emptyList()
        _peerIds.value = emptyMap()
        selectedPeerAddress = null
        resetConnectionState()
    }

    fun startPeerDiscovery() {
        if (!hasRequiredPermission() || _connectionState.value.isDiscovering || _connectionState.value.isConnected) {
            return
        }
        _connectionState.value = _connectionState.value.copy(isDiscovering = true)
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Unit
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "Wi-Fi Direct peer discovery failed: $reason")
                _connectionState.value = _connectionState.value.copy(isDiscovering = false)
            }
        })
    }

    /** Requests a Wi-Fi Direct group connection to the selected discovered device. */
    fun connect(peer: WifiP2pDevice) {
        if (!hasRequiredPermission() || peer.deviceAddress.isBlank()) return
        selectedPeerAddress = peer.deviceAddress
        _connectionState.value = _connectionState.value.copy(
            isDiscovering = false,
            isConnecting = true
        )
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Unit
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "Wi-Fi Direct connect to ${peer.deviceAddress} failed: $reason")
                _connectionState.value = _connectionState.value.copy(isConnecting = false)
            }
        })
    }

    private fun requestPeers() {
        if (!hasRequiredPermission()) return
        wifiP2pManager.requestPeers(channel) { peerList ->
            _discoveredPeers.value = peerList.deviceList.toList()
        }
    }

    /** Associates the selected Wi-Fi Direct device address with the peer ID from TCP HELLO. */
    fun associateSelectedPeerId(peerId: String) {
        val deviceAddress = selectedPeerAddress
            ?: _discoveredPeers.value.singleOrNull()?.deviceAddress
            ?: return
        if (peerId.isNotEmpty()) {
            _peerIds.value = _peerIds.value + (deviceAddress to peerId)
        }
    }

    private fun requestConnectionInfo() {
        if (!hasRequiredPermission()) return
        wifiP2pManager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
            _connectionState.value = WifiDirectConnectionState(
                isDiscovering = false,
                isConnecting = false,
                isConnected = info.groupFormed,
                isGroupOwner = info.groupFormed && info.isGroupOwner,
                groupOwnerIpAddress = info.groupOwnerAddress?.hostAddress
            )
        }
    }

    private fun resetConnectionState() {
        _connectionState.value = WifiDirectConnectionState()
    }

    private val silentActionListener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() = Unit
        override fun onFailure(reason: Int) = Unit
    }

    private fun hasRequiredPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "WifiDirectController"
    }
}
