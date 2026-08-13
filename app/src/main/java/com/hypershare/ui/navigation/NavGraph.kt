package com.hypershare.ui.navigation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hypershare.application.UserIdentityManager
import com.hypershare.model.PeerDiscoveryEvent
import com.hypershare.model.PeerMode
import com.hypershare.service.DiscoveryService
import com.hypershare.service.LanSocketManager
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
import kotlinx.serialization.Serializable

@Serializable data object HomeNavKey : NavKey
@Serializable data object PeerListNavKey : NavKey
@Serializable data class ChatNavKey(val peerId: String, val peerName: String = "", val peerIp: String = "", val peerPort: Int = 47200) : NavKey
@Serializable data object AccountSettingsNavKey : NavKey
@Serializable data object AppSettingsNavKey : NavKey
@Serializable data object FileBrowserNavKey : NavKey
@Serializable data object StreamPlayerNavKey : NavKey
@Serializable data object ModeToggleNavKey : NavKey
@Serializable data object RoutingDebugNavKey : NavKey
@Serializable data object SecurityPlaygroundNavKey : NavKey
@Serializable data object QrScannerNavKey : NavKey

@Composable
fun HyperShareNavGraph(
    targetPeerId: String? = null,
    onPeerNavigated: () -> Unit = {}
) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(HomeNavKey)

    var discoveryService by remember { mutableStateOf<DiscoveryService?>(null) }

    // Singleton LanSocketManager — same instance across all recompositions
    val lanSocketManager = remember { LanSocketManager.getInstance().also { it.startServer() } }

    val myPeerId = remember {
        UserIdentityManager.getInstance(context).getPeerId()
    }

    // Direct deep-link navigation to target chatroom when user taps notification
    LaunchedEffect(targetPeerId) {
        if (!targetPeerId.isNullOrEmpty()) {
            val activePeers = discoveryService?.getActivePeers() ?: emptyList()
            val peer = activePeers.find { it.peerId == targetPeerId }
            val ip = peer?.ipAddress?.hostAddress ?: ""
            val port = peer?.port ?: 47200

            // Add ChatNavKey for the notification's target peer
            backStack.add(ChatNavKey(peerId = targetPeerId, peerName = peer?.displayName ?: "", peerIp = ip, peerPort = port))
            onPeerNavigated()
        }
    }


    DisposableEffect(context) {
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

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeNavKey> {
                val vm: HomeViewModel = viewModel()
                val peerVm: PeerListViewModel = viewModel()
                HomeScreen(
                    viewModel = vm,
                    onOpenLocalModeChats = {
                        peerVm.setMode(PeerMode.MODE_1_WIFI)
                        backStack.add(PeerListNavKey)
                    },
                    onOpenEmergencyModeChats = {
                        peerVm.setMode(PeerMode.MODE_2_MESH)
                        backStack.add(PeerListNavKey)
                    },
                    onOpenAppSettings = { backStack.add(AppSettingsNavKey) },
                    onOpenAccountSettings = { backStack.add(AccountSettingsNavKey) },
                    onOpenQrScanner = { backStack.add(QrScannerNavKey) }
                )
            }
            entry<PeerListNavKey> {
                val vm: PeerListViewModel = viewModel()

                // Seed list with already-active peers when screen opens
                LaunchedEffect(discoveryService) {
                    discoveryService?.getActivePeers()?.forEach { peer ->
                        vm.handleDiscoveryEvent(PeerDiscoveryEvent.PeerDiscovered(peer))
                    }
                    // Then collect live events (replay=4 on the flow also helps catch recent ones)
                    discoveryService?.discoveryEventsFlow?.collect { event ->
                        vm.handleDiscoveryEvent(event)
                    }
                }

                PeerListScreen(
                    viewModel = vm,
                    onPeerClick = { peerId ->
                        // Look up the peer's resolved IP and name before navigating to chat
                        val peer = discoveryService?.getActivePeers()?.find { it.peerId == peerId }
                        val ip = peer?.ipAddress?.hostAddress ?: ""
                        val name = peer?.displayName ?: ""
                        backStack.add(ChatNavKey(peerId = peerId, peerName = name, peerIp = ip, peerPort = peer?.port ?: 47200))
                    },
                    onHomeClick = { backStack.add(HomeNavKey) },
                    onOpenAppSettings = { backStack.add(AppSettingsNavKey) },
                    onOpenAccountSettings = { backStack.add(AccountSettingsNavKey) },
                    onRefreshDiscovery = { discoveryService?.refreshDiscovery() }
                )
            }
            entry<ChatNavKey> { key ->
                val vm: ChatViewModel = viewModel()

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

                ChatScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<QrScannerNavKey> {
                val vm: com.hypershare.ui.qrscanner.QrScannerViewModel = viewModel()
                com.hypershare.ui.qrscanner.QrScannerScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() },
                    onOpenChat = { peerId ->
                        val activePeers = discoveryService?.getActivePeers() ?: emptyList()
                        val peer = activePeers.find { it.peerId == peerId }
                        val ip = peer?.ipAddress?.hostAddress ?: ""
                        val name = peer?.displayName ?: ""
                        backStack.add(ChatNavKey(peerId = peerId, peerName = name, peerIp = ip, peerPort = peer?.port ?: 47200))
                    }
                )
            }
            entry<AccountSettingsNavKey> {
                val vm: AccountSettingsViewModel = viewModel()
                AccountSettingsScreen(
                    viewModel = vm,
                    onHomeClick = { backStack.add(HomeNavKey) },
                    onOpenAppSettings = { backStack.add(AppSettingsNavKey) }
                )
            }
            entry<AppSettingsNavKey> {
                val vm: AppSettingsViewModel = viewModel()
                AppSettingsScreen(
                    viewModel = vm,
                    onOpenSecurityPlayground = { backStack.add(SecurityPlaygroundNavKey) },
                    onHomeClick = { backStack.add(HomeNavKey) },
                    onOpenAccountSettings = { backStack.add(AccountSettingsNavKey) }
                )
            }
            entry<FileBrowserNavKey> {
                val vm: FileBrowserViewModel = viewModel()
                FileBrowserScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<StreamPlayerNavKey> {
                val vm: StreamPlayerViewModel = viewModel()
                StreamPlayerScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<ModeToggleNavKey> {
                val vm: ModeToggleViewModel = viewModel()
                ModeToggleScreen(
                    viewModel = vm,
                    onDismiss = { backStack.removeLastOrNull() }
                )
            }
            entry<RoutingDebugNavKey> {
                val vm: RoutingDebugViewModel = viewModel()
                RoutingDebugScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<SecurityPlaygroundNavKey> {
                val vm: SecurityPlaygroundViewModel = viewModel()
                SecurityPlaygroundScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
