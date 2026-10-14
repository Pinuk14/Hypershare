package com.hypershare.ui.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hypershare.application.UserIdentityManager
import com.hypershare.model.PeerDiscoveryEvent
import com.hypershare.model.PeerMode
import com.hypershare.service.DiscoveryService
import com.hypershare.service.LanSocketManager
import com.hypershare.service.MeshNetworkService
import com.hypershare.ui.chat.ChatScreen
import com.hypershare.ui.chat.ChatViewModel
import com.hypershare.ui.components.NavTab
import com.hypershare.ui.filebrowser.FileBrowserScreen
import com.hypershare.ui.filebrowser.FileBrowserViewModel
import com.hypershare.ui.home.HomeScreen
import com.hypershare.ui.home.HomeViewModel
import com.hypershare.ui.modetoggle.ModeToggleScreen
import com.hypershare.ui.modetoggle.ModeToggleViewModel
import com.hypershare.ui.peerlist.PeerListScreen
import com.hypershare.ui.peerlist.PeerListViewModel
import com.hypershare.ui.routingdebug.RoutingDebugScreen
import com.hypershare.ui.routingdebug.RoutingDebugViewModel
import com.hypershare.ui.settings.AccountSettingsScreen
import com.hypershare.ui.settings.AccountSettingsViewModel
import com.hypershare.ui.settings.AppSettingsScreen
import com.hypershare.ui.settings.AppSettingsViewModel
import com.hypershare.ui.stream.StreamPlayerScreen
import com.hypershare.ui.stream.StreamPlayerViewModel
import com.hypershare.ui.testing.SecurityPlaygroundScreen
import com.hypershare.ui.testing.SecurityPlaygroundViewModel
import com.hypershare.ui.group.GroupListScreen
import com.hypershare.ui.group.GroupListViewModel
import com.hypershare.ui.group.GroupChatScreen
import com.hypershare.ui.group.GroupChatViewModel
import kotlinx.serialization.Serializable

@Serializable data object HomeNavKey : NavKey
@Serializable data class PeerListNavKey(val mode: PeerMode = PeerMode.MODE_1_WIFI) : NavKey
@Serializable data class ChatNavKey(val peerId: String, val peerName: String = "", val peerIp: String = "", val peerPort: Int = 47200) : NavKey
@Serializable data object AccountSettingsNavKey : NavKey
@Serializable data object AppSettingsNavKey : NavKey
@Serializable data object FileBrowserNavKey : NavKey
@Serializable data object StreamPlayerNavKey : NavKey
@Serializable data object ModeToggleNavKey : NavKey
@Serializable data object RoutingDebugNavKey : NavKey
@Serializable data object SecurityPlaygroundNavKey : NavKey
@Serializable data object QrScannerNavKey : NavKey
@Serializable data object GroupListNavKey : NavKey
@Serializable data class GroupChatNavKey(val groupId: String) : NavKey

@Composable
fun HyperShareNavGraph(
    targetPeerId: String? = null,
    networkPermissionsGranted: Boolean = false,
    onPeerNavigated: () -> Unit = {}
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(HomeNavKey)

    // Industry-grade back navigation primitives
    fun popBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
            return true
        }
        return false
    }

    fun navigateToHome() {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun navigateTopLevel(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        if (key == HomeNavKey) {
            navigateToHome()
            return
        }
        val existingIndex = backStack.indexOf(key)
        if (existingIndex >= 0) {
            while (backStack.size > existingIndex + 1) {
                backStack.removeLastOrNull()
            }
        } else {
            navigateToHome()
            backStack.add(key)
        }
    }

    fun pushDestination(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    // System Gesture / Hardware Back Button Handler
    BackHandler(enabled = backStack.size > 1) {
        popBack()
    }

    var discoveryService by remember { mutableStateOf<DiscoveryService?>(null) }
    var meshNetworkService by remember { mutableStateOf<MeshNetworkService?>(null) }

    // Singleton LanSocketManager — same instance across all recompositions
    val lanSocketManager = remember { LanSocketManager.getInstance().also { it.startServer() } }

    val myPeerId = remember {
        UserIdentityManager.getInstance(context).getPeerId()
    }

    fun hasRequiredNetworkPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun activateNetworkMode(mode: PeerMode) {
        if (!networkPermissionsGranted || !hasRequiredNetworkPermission()) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, DiscoveryService::class.java)
                .putExtra(MeshNetworkService.EXTRA_MODE, mode.name)
        )
        ContextCompat.startForegroundService(
            context,
            Intent(context, MeshNetworkService::class.java)
                .putExtra(MeshNetworkService.EXTRA_MODE, mode.name)
        )
    }

    // Direct deep-link navigation to target chatroom when user taps notification
    LaunchedEffect(targetPeerId) {
        if (!targetPeerId.isNullOrEmpty()) {
            val activePeers = discoveryService?.getActivePeers() ?: emptyList()
            val peer = activePeers.find { it.peerId == targetPeerId }
            val ip = peer?.ipAddress?.hostAddress ?: ""
            val port = peer?.port ?: 47200

            // Ensure back from notification cleanly pops to Home
            navigateToHome()
            pushDestination(ChatNavKey(peerId = targetPeerId, peerName = peer?.displayName ?: "", peerIp = ip, peerPort = port))
            onPeerNavigated()
        }
    }

    DisposableEffect(context, networkPermissionsGranted) {
        if (!networkPermissionsGranted) {
            onDispose { }
        } else {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? DiscoveryService.LocalBinder
                discoveryService = binder?.getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                discoveryService = null
            }
        }
        val intent = Intent(context, DiscoveryService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            try { context.unbindService(connection) } catch (e: Exception) { }
        }
        }
    }

    DisposableEffect(context, networkPermissionsGranted) {
        if (!networkPermissionsGranted) {
            onDispose { }
        } else {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? MeshNetworkService.LocalBinder
                meshNetworkService = binder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                meshNetworkService = null
            }
        }
        val intent = Intent(context, MeshNetworkService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            try { context.unbindService(connection) } catch (e: Exception) { }
        }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { popBack() },
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                val vm: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = vm,
                    onOpenLocalModeChats = {
                        activateNetworkMode(PeerMode.MODE_1_WIFI)
                        navigateTopLevel(PeerListNavKey(PeerMode.MODE_1_WIFI))
                    },
                    onOpenEmergencyModeChats = {
                        activateNetworkMode(PeerMode.MODE_2_MESH)
                        navigateTopLevel(PeerListNavKey(PeerMode.MODE_2_MESH))
                    },
                    onOpenAppSettings = { navigateTopLevel(AppSettingsNavKey) },
                    onOpenAccountSettings = { navigateTopLevel(AccountSettingsNavKey) },
                    onOpenQrScanner = { pushDestination(QrScannerNavKey) },
                    onOpenFileBrowser = { pushDestination(FileBrowserNavKey) }
                )
            }
            entry<PeerListNavKey> { key ->
                val vm: PeerListViewModel = viewModel()
                val currentContext = LocalContext.current

                LaunchedEffect(Unit) {
                    vm.initRepository(currentContext)
                }

                LaunchedEffect(key.mode) {
                    vm.setMode(key.mode)
                }

                // LAN peers remain sourced from the existing discovery service.
                LaunchedEffect(key.mode, discoveryService) {
                    if (key.mode == PeerMode.MODE_1_WIFI) {
                        discoveryService?.getActivePeers()?.forEach { peer ->
                            vm.handleDiscoveryEvent(PeerDiscoveryEvent.PeerDiscovered(peer))
                        }
                        discoveryService?.discoveryEventsFlow?.collect { event ->
                            vm.handleDiscoveryEvent(event)
                        }
                    }
                }

                // Wi-Fi Direct peers and group state come from the controller only in P2P mode.
                LaunchedEffect(key.mode, meshNetworkService) {
                    if (key.mode == PeerMode.MODE_2_MESH) {
                        meshNetworkService?.wifiDirectController?.discoveredPeers?.collect { peers ->
                            vm.updateWifiDirectPeers(peers)
                        }
                    }
                }
                LaunchedEffect(key.mode, meshNetworkService) {
                    if (key.mode == PeerMode.MODE_2_MESH) {
                        meshNetworkService?.wifiDirectController?.connectionState?.collect { state ->
                            vm.updateWifiDirectConnection(state)
                        }
                    }
                }
                LaunchedEffect(key.mode, meshNetworkService) {
                    if (key.mode == PeerMode.MODE_2_MESH) {
                        meshNetworkService?.wifiDirectController?.peerIds?.collect { peerIds ->
                            vm.updateWifiDirectPeerIds(peerIds)
                        }
                    }
                }

                PeerListScreen(
                    viewModel = vm,
                    onPeerClick = { peerId ->
                        // Look up the peer's resolved IP and name before navigating to chat
                        val activePeer = discoveryService?.getActivePeers()?.find { it.peerId == peerId }
                        val convItem = vm.uiState.value.conversationItems.find { it.peerId == peerId }
                        val ip = activePeer?.ipAddress?.hostAddress ?: convItem?.ipAddress ?: ""
                        val name = activePeer?.displayName ?: convItem?.displayName ?: ""
                        val port = activePeer?.port ?: convItem?.port ?: 47200
                        pushDestination(ChatNavKey(peerId = peerId, peerName = name, peerIp = ip, peerPort = port))
                    },
                    onWifiDirectPeerClick = { peer ->
                        val peerId = vm.uiState.value.wifiDirectPeerIds[peer.deviceAddress] ?: peer.deviceAddress
                        val connection = vm.uiState.value.wifiDirectConnection
                        if (connection.isConnected) {
                            val targetIp = if (connection.isGroupOwner) "" else connection.groupOwnerIpAddress.orEmpty()
                            pushDestination(
                                ChatNavKey(
                                    peerId = peerId,
                                    peerName = peer.deviceName.ifBlank { peer.deviceAddress },
                                    peerIp = targetIp
                                )
                            )
                        } else {
                            meshNetworkService?.wifiDirectController?.connect(peer)
                        }
                    },
                    onHomeClick = { navigateToHome() },
                    onBackClick = { popBack() },
                    onOpenAppSettings = { navigateTopLevel(AppSettingsNavKey) },
                    onOpenAccountSettings = { navigateTopLevel(AccountSettingsNavKey) },
                    onRefreshDiscovery = { discoveryService?.refreshDiscovery() },
                    onRefreshWifiDirectDiscovery = {
                        meshNetworkService?.wifiDirectController?.startPeerDiscovery()
                    }
                )
            }
            entry<ChatNavKey> { key ->
                val vm: ChatViewModel = viewModel(key = key.peerId)

                // Wire up real peer identity, IP address, and socket manager
                LaunchedEffect(key) {
                    vm.loadPeerMessages(
                        peerId = key.peerId,
                        peerName = key.peerName,
                        targetIp = key.peerIp,
                        myId = myPeerId,
                        msgRepo = null,
                        lanSocketMgr = lanSocketManager
                    )
                }

                // In Emergency Mode (no local IP), auto-initiate Wi-Fi Direct connection to the peer
                LaunchedEffect(key.peerId, key.peerIp, meshNetworkService) {
                    if (key.peerIp.isEmpty()) {
                        meshNetworkService?.requestConnectToPeer(key.peerId)
                    }
                }

                val p2pState = meshNetworkService?.wifiDirectController?.connectionState?.collectAsState()?.value

                ChatScreen(
                    viewModel = vm,
                    onBackClick = { popBack() },
                    onConnectWifiDirect = {
                        meshNetworkService?.requestConnectToPeer(key.peerId)
                    },
                    wifiDirectConnection = p2pState
                )
            }
            entry<QrScannerNavKey> {
                val vm: com.hypershare.ui.qrscanner.QrScannerViewModel = viewModel()
                com.hypershare.ui.qrscanner.QrScannerScreen(
                    viewModel = vm,
                    onBackClick = { popBack() },
                    onOpenChat = { peerId ->
                        val activePeers = discoveryService?.getActivePeers() ?: emptyList()
                        val peer = activePeers.find { it.peerId == peerId }
                        val ip = peer?.ipAddress?.hostAddress ?: ""
                        val name = peer?.displayName ?: ""
                        pushDestination(ChatNavKey(peerId = peerId, peerName = name, peerIp = ip, peerPort = peer?.port ?: 47200))
                    }
                )
            }
            entry<AccountSettingsNavKey> {
                val vm: AccountSettingsViewModel = viewModel()
                AccountSettingsScreen(
                    viewModel = vm,
                    onHomeClick = { navigateToHome() },
                    onBackClick = { popBack() },
                    onOpenAppSettings = { navigateTopLevel(AppSettingsNavKey) },
                    onOpenPeers = {
                        activateNetworkMode(PeerMode.MODE_1_WIFI)
                        navigateTopLevel(PeerListNavKey(PeerMode.MODE_1_WIFI))
                    }
                )
            }
            entry<AppSettingsNavKey> {
                val vm: AppSettingsViewModel = viewModel()
                AppSettingsScreen(
                    viewModel = vm,
                    onOpenSecurityPlayground = { pushDestination(SecurityPlaygroundNavKey) },
                    onHomeClick = { navigateToHome() },
                    onBackClick = { popBack() },
                    onOpenAccountSettings = { navigateTopLevel(AccountSettingsNavKey) },
                    onOpenPeers = {
                        activateNetworkMode(PeerMode.MODE_1_WIFI)
                        navigateTopLevel(PeerListNavKey(PeerMode.MODE_1_WIFI))
                    }
                )
            }
            entry<FileBrowserNavKey> {
                val vm: FileBrowserViewModel = viewModel()
                val activePeers = discoveryService?.getActivePeers() ?: emptyList()
                FileBrowserScreen(
                    viewModel = vm,
                    peers = activePeers,
                    onBackClick = { popBack() }
                )
            }
            entry<StreamPlayerNavKey> {
                val vm: StreamPlayerViewModel = viewModel()
                StreamPlayerScreen(
                    viewModel = vm,
                    onBackClick = { popBack() }
                )
            }
            entry<ModeToggleNavKey> {
                val vm: ModeToggleViewModel = viewModel()
                ModeToggleScreen(
                    viewModel = vm,
                    onDismiss = { popBack() }
                )
            }
            entry<RoutingDebugNavKey> {
                val vm: RoutingDebugViewModel = viewModel()
                RoutingDebugScreen(
                    viewModel = vm,
                    onBackClick = { popBack() }
                )
            }
            entry<SecurityPlaygroundNavKey> {
                val vm: SecurityPlaygroundViewModel = viewModel()
                SecurityPlaygroundScreen(
                    viewModel = vm,
                    onBackClick = { popBack() }
                )
            }
            entry<GroupListNavKey> {
                val vm: GroupListViewModel = viewModel()
                GroupListScreen(
                    viewModel = vm,
                    onGroupClick = { groupId ->
                        pushDestination(GroupChatNavKey(groupId = groupId))
                    },
                    onBackClick = { popBack() }
                )
            }
            entry<GroupChatNavKey> { key ->
                val vm: GroupChatViewModel = viewModel(key = key.groupId)
                LaunchedEffect(key) {
                    vm.loadGroup(key.groupId)
                }
                GroupChatScreen(
                    viewModel = vm,
                    onBackClick = { popBack() }
                )
            }
        }
    )
}
