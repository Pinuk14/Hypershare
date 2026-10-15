package com.hypershare.ui.peerlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.wifi.p2p.WifiP2pDevice
import com.hypershare.db.MessageRepository
import com.hypershare.db.PeerConversation
import com.hypershare.model.ConnectedPeer
import com.hypershare.model.PeerDiscoveryEvent
import com.hypershare.model.PeerMode
import com.hypershare.model.PeerStatus
import com.hypershare.service.WifiDirectConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PeerUiItem(
    val peerId: String,
    val displayName: String,
    val lastActivity: Long,
    val lastMessageSnippet: String? = null,
    val unreadCount: Int = 0,
    val isTrusted: Boolean = false,
    val hasPeerAccepted: Boolean = false,
    val isOnlineOnLan: Boolean = false,
    val ipAddress: String? = null,
    val port: Int = 47200,
    val isNearbyWifiDirect: Boolean = false,
    val wifiDirectDevice: WifiP2pDevice? = null,
    val wifiDirectDeviceAddress: String? = null,
    val p2pStatus: PeerStatus = PeerStatus.DISCOVERED
)

data class PeerListUiState(
    val peers: List<ConnectedPeer> = emptyList(),
    val conversationItems: List<PeerUiItem> = emptyList(),
    val currentMode: PeerMode = PeerMode.MODE_1_WIFI,
    val isRefreshing: Boolean = false,
    val wifiDirectPeers: List<WifiP2pDevice> = emptyList(),
    val wifiDirectConnection: WifiDirectConnectionState = WifiDirectConnectionState(),
    val wifiDirectPeerIds: Map<String, String> = emptyMap()
)

class PeerListViewModel(
    private var repository: MessageRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(PeerListUiState())
    val uiState: StateFlow<PeerListUiState> = _uiState.asStateFlow()

    private var dbConversations: List<PeerConversation> = emptyList()
    private var myPeerId: String = ""
    private var myUsername: String = ""
    private var myLocalIp: String? = null

    init {
        // Continuous liveness reaper: prune any LAN peer not seen in 6 seconds (3 missed pings)
        viewModelScope.launch {
            while (isActive) {
                delay(2000L)
                val now = System.currentTimeMillis()
                val current = _uiState.value.peers
                val alive = current.filter { now - it.lastSeenTimestamp <= 24000L }
                if (alive.size != current.size) {
                    _uiState.value = _uiState.value.copy(peers = alive)
                    recomputeConversationItems()
                }
            }
        }
    }

    fun initRepository(context: Context) {
        val identity = com.hypershare.application.UserIdentityManager.getInstance(context)
        myPeerId = identity.getPeerId()
        myUsername = identity.getUsername()
        myLocalIp = try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) android.text.format.Formatter.formatIpAddress(ip) else null
        } catch (_: Exception) { null }

        if (repository == null) {
            repository = MessageRepository(context.applicationContext)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository?.cleanupGhostAndDemoData(myPeerId, myUsername)
            loadConversations()
        }
    }

    fun loadConversations() {
        val repo = repository ?: return
        viewModelScope.launch {
            val convs = withContext(Dispatchers.IO) {
                repo.getAllConversations(myPeerId)
            }
            dbConversations = convs.filterNot {
                it.peerId == myPeerId ||
                it.peerId == "local" ||
                it.peerId.contains("(") ||
                it.displayName.contains("(")
            }
            recomputeConversationItems()
        }
    }

    fun setMode(mode: PeerMode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
        recomputeConversationItems()
    }

    fun toggleMode() {
        val nextMode = if (_uiState.value.currentMode == PeerMode.MODE_1_WIFI) {
            PeerMode.MODE_2_MESH
        } else {
            PeerMode.MODE_1_WIFI
        }
        _uiState.value = _uiState.value.copy(currentMode = nextMode)
        recomputeConversationItems()
    }

    fun refreshPeers(onTriggerRefresh: (() -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, peers = emptyList())
            recomputeConversationItems()
            loadConversations()
            onTriggerRefresh?.invoke()
            delay(1200L)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun handleDiscoveryEvent(event: PeerDiscoveryEvent) {
        when (event) {
            is PeerDiscoveryEvent.PeerDiscovered -> {
                val newPeer = event.peer
                val newIp = newPeer.ipAddress?.hostAddress
                val currentPeers = _uiState.value.peers.filterNot { existing ->
                    existing.peerId == newPeer.peerId ||
                    (newIp != null && existing.ipAddress?.hostAddress == newIp)
                }.toMutableList()
                currentPeers.add(newPeer)
                _uiState.value = _uiState.value.copy(peers = currentPeers)
            }
            is PeerDiscoveryEvent.PeerLost -> {
                fun clean(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]"), "")
                val eventClean = clean(event.peerId)
                val currentPeers = _uiState.value.peers.filterNot { existing ->
                    existing.peerId == event.peerId ||
                    existing.ipAddress?.hostAddress == event.peerId ||
                    clean(existing.displayName) == eventClean ||
                    clean(existing.peerId) == eventClean
                }
                _uiState.value = _uiState.value.copy(peers = currentPeers)
            }
        }
        recomputeConversationItems()
    }

    fun updateWifiDirectPeers(peers: List<WifiP2pDevice>) {
        _uiState.value = _uiState.value.copy(wifiDirectPeers = peers)
        recomputeConversationItems()
    }

    fun updateWifiDirectConnection(state: WifiDirectConnectionState) {
        _uiState.value = _uiState.value.copy(wifiDirectConnection = state)
        recomputeConversationItems()
    }

    fun updateWifiDirectPeerIds(peerIds: Map<String, String>) {
        _uiState.value = _uiState.value.copy(wifiDirectPeerIds = peerIds)
        recomputeConversationItems()
    }

    private fun normalizeName(name: String): String = name.lowercase()
        .replace(Regex("\\(.*"), "")
        .replace(Regex("[^a-z0-9]"), "")
        .trim()

    private fun recomputeConversationItems() {
        val isEmergency = _uiState.value.currentMode == PeerMode.MODE_2_MESH
        val lanMap = _uiState.value.peers.associateBy { it.peerId }
        val wifiDirectMap = mutableMapOf<String, WifiP2pDevice>()
        for (device in _uiState.value.wifiDirectPeers) {
            val mappedPeerId = _uiState.value.wifiDirectPeerIds[device.deviceAddress] ?: device.deviceAddress
            wifiDirectMap[mappedPeerId] = device
        }

        val itemsMap = LinkedHashMap<String, PeerUiItem>()

        // 1. Populate from ever-connected DB conversations
        // In Emergency mode, ONLY show contacts who have been mutually accepted (trusted & accepted)
        val sourceConvs = if (isEmergency) {
            dbConversations.filter { it.isTrusted && it.hasPeerAccepted }
        } else {
            dbConversations
        }

        for (conv in sourceConvs) {
            val cleanConvName = normalizeName(conv.displayName)
            val lanPeer = if (isEmergency) null else (lanMap[conv.peerId] ?: _uiState.value.peers.find { 
                normalizeName(it.displayName) == cleanConvName 
            })
            val wifiDevice = wifiDirectMap[conv.peerId]
            val p2pStatus = when {
                _uiState.value.wifiDirectConnection.isConnected -> PeerStatus.CONNECTED
                _uiState.value.wifiDirectConnection.isConnecting -> PeerStatus.CONNECTING
                else -> PeerStatus.DISCOVERED
            }

            itemsMap[conv.peerId] = PeerUiItem(
                peerId = conv.peerId,
                displayName = conv.displayName,
                lastActivity = conv.lastActivity,
                lastMessageSnippet = conv.lastMessageSnippet,
                unreadCount = conv.unreadCount,
                isTrusted = conv.isTrusted,
                hasPeerAccepted = conv.hasPeerAccepted,
                isOnlineOnLan = if (isEmergency) false else (lanPeer != null),
                ipAddress = if (isEmergency) null else lanPeer?.ipAddress?.hostAddress,
                port = lanPeer?.port ?: 47200,
                isNearbyWifiDirect = if (isEmergency) false else (wifiDevice != null),
                wifiDirectDevice = wifiDevice,
                wifiDirectDeviceAddress = wifiDevice?.deviceAddress,
                p2pStatus = p2pStatus
            )
        }

        // 2. Add any newly discovered LAN peers not yet in database (Local Mode only)
        if (!isEmergency) {
            for (peer in _uiState.value.peers) {
                val alreadyInMap = itemsMap.containsKey(peer.peerId)
                if (!alreadyInMap) {
                    val isTrustedInRepo = repository?.isPeerTrusted(peer.peerId) ?: false
                    val hasAcceptedInRepo = repository?.hasPeerAcceptedUs(peer.peerId) ?: false
                    itemsMap[peer.peerId] = PeerUiItem(
                        peerId = peer.peerId,
                        displayName = peer.displayName,
                        lastActivity = peer.lastSeenTimestamp,
                        lastMessageSnippet = null,
                        unreadCount = 0,
                        isTrusted = isTrustedInRepo,
                        hasPeerAccepted = hasAcceptedInRepo,
                        isOnlineOnLan = true,
                        ipAddress = peer.ipAddress?.hostAddress,
                        port = peer.port
                    )
                }
            }
        }

        // Note: Step 3 (raw Wi-Fi Direct devices) is intentionally omitted so raw MAC addresses
        // and non-Hypershare devices (such as Miracast laptops) never show as conversation cards.

        // 3. Deduplicate items by IP Address or normalized name to eliminate ghost/duplicate cards
        val cleanMyName = normalizeName(myUsername)
        val deduplicatedItems = mutableMapOf<String, PeerUiItem>()
        for (item in itemsMap.values) {
            val cleanItemName = normalizeName(item.displayName)

            // Filter self device completely by peerId, "local", or local IP (never by username)
            if (item.peerId == myPeerId ||
                item.peerId == "local" ||
                (!myLocalIp.isNullOrEmpty() && item.ipAddress == myLocalIp)
            ) {
                continue
            }

            // In Emergency mode, strictly enforce mutual acceptance
            if (isEmergency && (!item.isTrusted || !item.hasPeerAccepted)) {
                continue
            }

            // Always key by normalized name first to merge offline and online cards for the same physical peer
            val nameKey = "name:$cleanItemName"
            val ipKey = if (!item.ipAddress.isNullOrEmpty()) "ip:${item.ipAddress}" else null

            // Find match by name OR by IP
            val matchedKey = when {
                deduplicatedItems.containsKey(nameKey) -> nameKey
                ipKey != null && deduplicatedItems.containsKey(ipKey) -> ipKey
                else -> null
            }

            if (matchedKey == null) {
                deduplicatedItems[nameKey] = item
                if (ipKey != null) deduplicatedItems[ipKey] = item
            } else {
                val existing = deduplicatedItems[matchedKey]!!
                val isCurrentReal = item.peerId.length >= 16 || item.peerId.startsWith("peer-")
                val isExistingReal = existing.peerId.length >= 16 || existing.peerId.startsWith("peer-")

                val preferred = when {
                    item.isOnlineOnLan && !existing.isOnlineOnLan -> item
                    existing.isOnlineOnLan && !item.isOnlineOnLan -> existing
                    isCurrentReal && !isExistingReal -> item
                    isExistingReal && !isCurrentReal -> existing
                    item.lastActivity > existing.lastActivity -> item
                    else -> existing
                }

                val merged = preferred.copy(
                    lastMessageSnippet = preferred.lastMessageSnippet ?: (if (preferred == item) existing.lastMessageSnippet else item.lastMessageSnippet),
                    unreadCount = maxOf(item.unreadCount, existing.unreadCount),
                    isTrusted = item.isTrusted || existing.isTrusted,
                    hasPeerAccepted = item.hasPeerAccepted || existing.hasPeerAccepted,
                    isOnlineOnLan = if (isEmergency) false else (item.isOnlineOnLan || existing.isOnlineOnLan),
                    isNearbyWifiDirect = if (isEmergency) false else (item.isNearbyWifiDirect || existing.isNearbyWifiDirect),
                    ipAddress = if (!isEmergency && (item.isOnlineOnLan || existing.isOnlineOnLan)) {
                        (if (preferred.isOnlineOnLan) preferred.ipAddress else null)
                            ?: (if (item.isOnlineOnLan) item.ipAddress else null)
                            ?: (if (existing.isOnlineOnLan) existing.ipAddress else null)
                    } else null
                )
                deduplicatedItems[nameKey] = merged
                if (ipKey != null) deduplicatedItems[ipKey] = merged
                if (!existing.ipAddress.isNullOrEmpty()) {
                    deduplicatedItems["ip:${existing.ipAddress}"] = merged
                }
            }
        }

        // Stable WhatsApp-style sorting by latest message/activity timestamp, distinct by peerId
        val sorted = deduplicatedItems.values.distinctBy { it.peerId }.sortedByDescending { it.lastActivity }
        _uiState.value = _uiState.value.copy(conversationItems = sorted)
    }
}
