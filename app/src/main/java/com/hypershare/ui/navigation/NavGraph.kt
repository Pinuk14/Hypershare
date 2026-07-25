package com.hypershare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.hypershare.ui.chat.ChatScreen
import com.hypershare.ui.chat.ChatViewModel
import com.hypershare.ui.filebrowser.FileBrowserScreen
import com.hypershare.ui.filebrowser.FileBrowserViewModel
import com.hypershare.ui.modetoggle.ModeToggleScreen
import com.hypershare.ui.modetoggle.ModeToggleViewModel
import com.hypershare.ui.peerlist.PeerListScreen
import com.hypershare.ui.peerlist.PeerListViewModel
import com.hypershare.ui.routingdebug.RoutingDebugScreen
import com.hypershare.ui.routingdebug.RoutingDebugViewModel
import com.hypershare.ui.stream.StreamPlayerScreen
import com.hypershare.ui.stream.StreamPlayerViewModel
import kotlinx.serialization.Serializable

@Serializable data object PeerListNavKey : NavKey
@Serializable data class ChatNavKey(val peerId: String) : NavKey
@Serializable data object FileBrowserNavKey : NavKey
@Serializable data object StreamPlayerNavKey : NavKey
@Serializable data object ModeToggleNavKey : NavKey
@Serializable data object RoutingDebugNavKey : NavKey

@Composable
fun HyperShareNavGraph() {
    val backStack = rememberNavBackStack(PeerListNavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<PeerListNavKey> {
                val vm: PeerListViewModel = viewModel()
                PeerListScreen(
                    viewModel = vm,
                    onPeerClick = { peerId -> backStack.add(ChatNavKey(peerId)) },
                    onOpenModeToggle = { backStack.add(ModeToggleNavKey) }
                )
            }
            entry<ChatNavKey> {
                val vm: ChatViewModel = viewModel()
                ChatScreen(
                    viewModel = vm,
                    onBackClick = { backStack.removeLastOrNull() }
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
        }
    )
}
