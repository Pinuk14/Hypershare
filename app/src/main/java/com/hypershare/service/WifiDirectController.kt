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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Android Wi-Fi Direct discovery and group formation for the P2P milestone.
 * Socket, routing, transfer, and encryption work intentionally remain outside this controller.
 */
data class WifiDirectConnectionState(
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerIpAddress: String? = null,
    val clientCount: Int = 0
)

class WifiDirectController(context: Context) {

    private val appContext = context.applicationContext
    private val wifiP2pManager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectionWatchdogJob: Job? = null

    private fun startConnectionWatchdog(timeoutMs: Long = 15000L) {
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = controllerScope.launch {
            delay(timeoutMs)
            if (_connectionState.value.isConnecting && !_connectionState.value.isConnected) {
                Log.w(TAG, "Wi-Fi Direct connection negotiation timed out after ${timeoutMs}ms. Forcing reset and restarting discovery.")
                disconnect {
                    startPeerDiscovery()
                }
            }
        }
    }

    private fun cancelConnectionWatchdog() {
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
    }

    private fun ensureChannel(): WifiP2pManager.Channel? {
        if (channel == null) {
            channel = wifiP2pManager.initialize(appContext, Looper.getMainLooper()) {
                Log.w(TAG, "Wi-Fi Direct channel disconnected, will reinitialize on demand")
                channel = null
                resetConnectionState()
            }
        }
        return channel
    }

    private val _discoveredPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiP2pDevice>> = _discoveredPeers.asStateFlow()

    private val _connectionState = MutableStateFlow(WifiDirectConnectionState())
    val connectionState: StateFlow<WifiDirectConnectionState> = _connectionState.asStateFlow()

    private val _peerIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerIds: StateFlow<Map<String, String>> = _peerIds.asStateFlow()

    val p2pPrefs = appContext.getSharedPreferences("hypershare_p2p_mappings", Context.MODE_PRIVATE)

    var myDeviceAddress: String? = p2pPrefs.getString("my_mac", null)
        private set
    var myDeviceName: String? = p2pPrefs.getString("my_name", null)
        private set

    private var receiverRegistered = false
    private var selectedPeerAddress: String? = null
    private var lastConnectAttemptTimestamp = 0L
    private var connectingTargetAddress: String? = null

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

                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                    val discoveryState = intent.getIntExtra(
                        WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
                    )
                    val isDisc = discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                    Log.d(TAG, "Wi-Fi Direct discovery state changed: isDiscovering=$isDisc")
                    _connectionState.value = _connectionState.value.copy(isDiscovering = isDisc)
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val thisDevice = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    if (thisDevice != null) {
                        myDeviceAddress = thisDevice.deviceAddress
                        myDeviceName = thisDevice.deviceName
                        p2pPrefs.edit()
                            .putString("my_mac", thisDevice.deviceAddress)
                            .putString("my_name", thisDevice.deviceName)
                            .apply()
                        Log.d(TAG, "Local P2P Device updated: name=${thisDevice.deviceName}, address=${thisDevice.deviceAddress}")
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    cancelConnectionWatchdog()
                    if (networkInfo?.isConnected == true) {
                        connectingTargetAddress = null
                        requestConnectionInfo()
                    } else {
                        resetConnectionState()
                    }
                }
            }
        }
    }

    /** Gracefully disconnects active P2P group or cancels ongoing connection negotiation. */
    fun disconnect(onComplete: (() -> Unit)? = null) {
        cancelConnectionWatchdog()
        if (!hasRequiredPermission()) {
            onComplete?.invoke()
            return
        }
        val ch = ensureChannel() ?: run {
            onComplete?.invoke()
            return
        }
        if (_connectionState.value.isConnecting) {
            try {
                wifiP2pManager.cancelConnect(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        resetConnectionState()
                        onComplete?.invoke()
                    }
                    override fun onFailure(reason: Int) {
                        resetConnectionState()
                        onComplete?.invoke()
                    }
                })
            } catch (_: Exception) {
                resetConnectionState()
                onComplete?.invoke()
            }
        } else {
            try {
                wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        resetConnectionState()
                        onComplete?.invoke()
                    }
                    override fun onFailure(reason: Int) {
                        resetConnectionState()
                        onComplete?.invoke()
                    }
                })
            } catch (_: Exception) {
                resetConnectionState()
                onComplete?.invoke()
            }
        }
    }

    /** Performs clean-slate framework recovery by cancelling any pending connect, removing stale groups, and cycling channel. */
    fun resetWifiDirect(onComplete: (() -> Unit)? = null) {
        cancelConnectionWatchdog()
        disconnect {
            channel = null
            resetConnectionState()
            ensureChannel()
            startPeerDiscovery()
            onComplete?.invoke()
        }
    }

    /** Registers for P2P broadcasts and begins device discovery with clean slate. */
    fun start() {
        if (!hasRequiredPermission()) {
            Log.w(TAG, "Wi-Fi Direct start ignored because the required runtime permission is not granted")
            return
        }
        val ch = ensureChannel() ?: return
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            wifiP2pManager.requestDeviceInfo(ch) { device ->
                if (device != null) {
                    myDeviceAddress = device.deviceAddress
                    myDeviceName = device.deviceName
                    p2pPrefs.edit()
                        .putString("my_mac", device.deviceAddress)
                        .putString("my_name", device.deviceName)
                        .apply()
                    Log.d(TAG, "Local device info resolved via API 29+: name=${device.deviceName}, address=${device.deviceAddress}")
                }
            }
        }
        // Always reset stale framework groups on start before fresh discovery
        resetWifiDirect {
            startPeerDiscovery()
        }
    }

    /** Stops active discovery and unregisters the dynamic Wi-Fi Direct receiver. */
    fun stop() {
        cancelConnectionWatchdog()
        if (!receiverRegistered && !_connectionState.value.isDiscovering) return
        val ch = ensureChannel()
        if (ch != null && hasRequiredPermission()) {
            if (_connectionState.value.isConnecting) {
                wifiP2pManager.cancelConnect(ch, silentActionListener)
            }
            if (_connectionState.value.isConnected) {
                wifiP2pManager.removeGroup(ch, silentActionListener)
            }
            wifiP2pManager.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
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

    fun startPeerDiscovery(forceClean: Boolean = false) {
        if (!hasRequiredPermission()) return
        if (forceClean) {
            resetWifiDirect { startPeerDiscovery(false) }
            return
        }
        if ((_connectionState.value.isConnected && !_connectionState.value.isGroupOwner) || _connectionState.value.isConnecting) return
        val ch = ensureChannel() ?: return

        _connectionState.value = _connectionState.value.copy(isDiscovering = true)
        wifiP2pManager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Wi-Fi Direct peer discovery started successfully")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "Wi-Fi Direct peer discovery failed: reason=$reason")
                _connectionState.value = _connectionState.value.copy(isDiscovering = false)
                if (reason == WifiP2pManager.BUSY) {
                    // Framework is busy or stuck with previous P2P group -> auto-clean and retry
                    resetWifiDirect {
                        ensureChannel()?.let { freshCh ->
                            wifiP2pManager.discoverPeers(freshCh, silentActionListener)
                        }
                    }
                }
            }
        })
    }

    /** Requests a Wi-Fi Direct group connection to the selected discovered device. */
    fun connect(peer: WifiP2pDevice, preferredIntent: Int = 15, onResult: ((Boolean) -> Unit)? = null) {
        connect(peer.deviceAddress, preferredIntent, onResult)
    }

    fun connect(targetMac: String, preferredIntent: Int = 15, onResult: ((Boolean) -> Unit)? = null) {
        if (!hasRequiredPermission() || targetMac.isBlank()) {
            onResult?.invoke(false)
            return
        }
        val ch = ensureChannel() ?: run {
            onResult?.invoke(false)
            return
        }
        selectedPeerAddress = targetMac
        connectingTargetAddress = targetMac
        _connectionState.value = _connectionState.value.copy(
            isDiscovering = false,
            isConnecting = true
        )
        startConnectionWatchdog()
        val config = WifiP2pConfig().apply {
            deviceAddress = targetMac
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = preferredIntent
        }
        wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Wi-Fi Direct connect accepted for $targetMac with intent $preferredIntent")
                onResult?.invoke(true)
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "Wi-Fi Direct connect to $targetMac failed: reason=$reason")
                cancelConnectionWatchdog()
                _connectionState.value = _connectionState.value.copy(isConnecting = false)
                connectingTargetAddress = null
                onResult?.invoke(false)
                if (reason == WifiP2pManager.BUSY) {
                    resetWifiDirect { startPeerDiscovery() }
                } else {
                    startPeerDiscovery()
                }
            }
        })
    }

    fun requestPeers() {
        if (!hasRequiredPermission()) return
        val ch = ensureChannel() ?: return
        wifiP2pManager.requestPeers(ch) { peerList ->
            val list = peerList.deviceList.toList()
            _discoveredPeers.value = list
            Log.d(TAG, "Discovered ${list.size} Wi-Fi Direct devices")
        }
    }

    /** Associates the selected Wi-Fi Direct device address with the peer ID from TCP HELLO. */
    fun associateSelectedPeerId(peerId: String, macAddress: String? = null) {
        val deviceAddress = macAddress
            ?: selectedPeerAddress
            ?: _discoveredPeers.value.singleOrNull()?.deviceAddress
            ?: return
        if (peerId.isNotEmpty()) {
            _peerIds.value = _peerIds.value + (deviceAddress to peerId)
            p2pPrefs.edit()
                .putString("peer:$peerId", deviceAddress)
                .putString("mac:$deviceAddress", peerId)
                .apply()
            com.hypershare.db.MessageRepository(appContext).savePeerP2pMac(peerId, deviceAddress)
            Log.d(TAG, "Associated and persisted P2P mapping: peerId=$peerId <-> deviceAddress=$deviceAddress")
        }
    }

    fun getRememberedMacForPeer(peerId: String): String? {
        val fromPrefs = p2pPrefs.getString("peer:$peerId", null)
        if (!fromPrefs.isNullOrEmpty()) return fromPrefs
        val fromRepo = com.hypershare.db.MessageRepository(appContext).getPeerP2pMac(peerId)
        if (!fromRepo.isNullOrEmpty()) {
            p2pPrefs.edit().putString("peer:$peerId", fromRepo).apply()
            return fromRepo
        }
        return null
    }

    fun getRememberedPeerForMac(deviceAddress: String): String? {
        val fromPrefs = p2pPrefs.getString("mac:$deviceAddress", null)
        if (!fromPrefs.isNullOrEmpty()) return fromPrefs
        val fromRepo = com.hypershare.db.MessageRepository(appContext).getPeerIdByP2pMac(deviceAddress)
        if (!fromRepo.isNullOrEmpty()) {
            p2pPrefs.edit().putString("mac:$deviceAddress", fromRepo).apply()
            return fromRepo
        }
        return null
    }

    /** Excludes non-mobile appliances (PCs, TVs, streaming sticks) from automated P2P connections. */
    fun isBlacklistedDevice(name: String): Boolean {
        val upper = name.uppercase()
        return upper.startsWith("DESKTOP-") ||
               upper.startsWith("LAPTOP-") ||
               upper.startsWith("DIRECT-") ||
               upper.contains("BRAVIA") ||
               upper.contains("TV") ||
               upper.contains("ROKU") ||
               upper.contains("CHROMECAST") ||
               upper.contains("PROJECTOR")
    }

    /**
     * Deterministically calculates Group Owner Intent (15 vs 0) between this device and target peer.
     * When both devices calculate intents symmetrically, one will always be 15 and the other 0,
     * allowing zero-touch mutual Wi-Fi Direct WPS PBC negotiation without tie-breaker collisions.
     */
    fun calculateGoIntent(targetMac: String, myPeerId: String = "", targetPeerId: String = ""): Int {
        // If this device is ALREADY the group owner, maintain GO intent (15)
        if (_connectionState.value.isConnected && _connectionState.value.isGroupOwner) {
            return 15
        }
        // If connecting as a client to an already formed group, request client role (0)
        if (_connectionState.value.isConnected && !_connectionState.value.isGroupOwner) {
            return 0
        }
        if (myPeerId.isNotEmpty() && targetPeerId.isNotEmpty() && myPeerId != targetPeerId) {
            return if (myPeerId >= targetPeerId) 15 else 0
        }
        val myMac = myDeviceAddress.orEmpty()
        val isValidMyMac = myMac.isNotEmpty() && myMac != "02:00:00:00:00:00"
        val isValidTargetMac = targetMac.isNotEmpty() && targetMac != "02:00:00:00:00:00"
        return if (isValidMyMac && isValidTargetMac && myMac != targetMac) {
            if (myMac >= targetMac) 15 else 0
        } else {
            15
        }
    }

    /** Automatically connects to the designated Wi-Fi Direct peer with specified GO intent. */
    fun autoConnectToPeer(peer: WifiP2pDevice, preferredIntent: Int = 15): Boolean {
        if (!hasRequiredPermission() || peer.deviceAddress.isBlank()) return false
        if (_connectionState.value.isConnected || _connectionState.value.isConnecting) return false

        val now = System.currentTimeMillis()
        if (connectingTargetAddress == peer.deviceAddress && (now - lastConnectAttemptTimestamp) < 6000L) {
            return false
        }

        lastConnectAttemptTimestamp = now
        connectingTargetAddress = peer.deviceAddress
        selectedPeerAddress = peer.deviceAddress

        Log.d(TAG, "Initiating auto-connect to ${peer.deviceName} (${peer.deviceAddress}) with GO intent $preferredIntent")
        connect(peer.deviceAddress, preferredIntent)
        return true
    }

    private fun requestConnectionInfo(retryCount: Int = 0) {
        if (!hasRequiredPermission()) return
        val ch = ensureChannel() ?: return
        wifiP2pManager.requestConnectionInfo(ch) { info: WifiP2pInfo ->
            val hostAddress = info.groupOwnerAddress?.hostAddress
            if (info.groupFormed && hostAddress.isNullOrEmpty() && retryCount < 8) {
                Log.d(TAG, "Wi-Fi Direct group formed but groupOwnerAddress is null (DHCP in progress). Retrying ($retryCount/8)...")
                controllerScope.launch {
                    delay(400)
                    requestConnectionInfo(retryCount + 1)
                }
                return@requestConnectionInfo
            }
            if (info.groupFormed && info.isGroupOwner) {
                wifiP2pManager.requestGroupInfo(ch) { group ->
                    val count = group?.clientList?.size ?: 0
                    Log.d(TAG, "GO group info updated: clientCount=$count clients=${group?.clientList?.map { it.deviceName }}")
                    _connectionState.value = WifiDirectConnectionState(
                        isDiscovering = _connectionState.value.isDiscovering,
                        isConnecting = false,
                        isConnected = true,
                        isGroupOwner = true,
                        groupOwnerIpAddress = hostAddress,
                        clientCount = count
                    )
                }
            } else {
                _connectionState.value = WifiDirectConnectionState(
                    isDiscovering = false,
                    isConnecting = false,
                    isConnected = info.groupFormed,
                    isGroupOwner = info.groupFormed && info.isGroupOwner,
                    groupOwnerIpAddress = hostAddress,
                    clientCount = 0
                )
            }
        }
    }

    private fun resetConnectionState() {
        cancelConnectionWatchdog()
        connectingTargetAddress = null
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
