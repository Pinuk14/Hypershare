package com.hypershare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hypershare.model.PeerMode
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
@Serializable data class ChatNavKey(val peerId: String) : NavKey
@Serializable data object AccountSettingsNavKey : NavKey
@Serializable data object AppSettingsNavKey : NavKey
@Serializable data object FileBrowserNavKey : NavKey
@Serializable data object StreamPlayerNavKey : NavKey
@Serializable data object ModeToggleNavKey : NavKey
@Serializable data object RoutingDebugNavKey : NavKey
@Serializable data object SecurityPlaygroundNavKey : NavKey

@Composable
fun HyperShareNavGraph() {
    val backStack = rememberNavBackStack(HomeNavKey)

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
                    onOpenAccountSettings = { backStack.add(AccountSettingsNavKey) }
                )
            }
            entry<PeerListNavKey> {
                val vm: PeerListViewModel = viewModel()
                PeerListScreen(
                    viewModel = vm,
                    onPeerClick = { peerId -> backStack.add(ChatNavKey(peerId)) },
                    onHomeClick = { backStack.add(HomeNavKey) },
                    onOpenAppSettings = { backStack.add(AppSettingsNavKey) },
                    onOpenAccountSettings = { backStack.add(AccountSettingsNavKey) }
                )
            }
            entry<ChatNavKey> {
                val vm: ChatViewModel = viewModel()
                ChatScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
                )
            }
            entry<AccountSettingsNavKey> {
                val vm: AccountSettingsViewModel = viewModel()
                AccountSettingsScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() },
                    onHomeClick = { backStack.add(HomeNavKey) }
                )
            }
            entry<AppSettingsNavKey> {
                val vm: AppSettingsViewModel = viewModel()
                AppSettingsScreen(
                    viewModel = vm,
                    onOpenSecurityPlayground = { backStack.add(SecurityPlaygroundNavKey) },
                    onBackClick = { backStack.removeLastOrNull() },
                    onHomeClick = { backStack.add(HomeNavKey) }
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
