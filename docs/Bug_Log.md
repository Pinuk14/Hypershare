# HyperShare — Bug Log & Production Issues Register

> **Maintenance Guideline**: Record all production bugs, edge-case failures, hardware/OEM quirks, and runtime exceptions encountered during development and testing here.  
> Update status and resolution as issues are triaged and fixed.

---

## 1. Issue Severity Standards

| Severity | Definition | SLA / Target Resolution |
|---|---|---|
| **P0 — Critical** | App crash, security vulnerability, mesh network deadlock, data corruption | Immediate block / Fix within 24h |
| **P1 — High** | Mode transition failure, chunk ACK timeout, socket leak, battery drain | Fix before phase release |
| **P2 — Medium** | UI glitch, delayed peer discovery (>5s), formatting error | Fix during weekly polish |
| **P3 — Low** | Minor cosmetic issue, non-critical log warning | Backlog |

---

## 2. Issue Register

### [ISSUE-004] QR Code Share ID Scan & Security Key Exchange Workflow
- **Severity**: P1 — High
- **Phase/Week**: Phase 1 — Week 7
- **Component**: `ui/qrscanner/QrScannerScreen.kt`, `ui/qrscanner/QrScannerViewModel.kt`, `identity/IdentityManager.kt`, `security/TofuManager.kt`
- **Environment**: All Android devices with camera support
- **Description**: Scanning a peer's QR Share ID on the Scan screen must extract the peer's public key fingerprint and device metadata, perform ECDH shared key derivation, verify TOFU (Trust-On-First-Use) key store pinning, and authorize encrypted peer-to-peer communication.
- **Root Cause**: CameraX preview, ZXing BitMatrix decoding, and Ed25519 signature verification needed end-to-end integration into navigation flow.
- **Resolution**: Built `QrScannerScreen.kt` and `QrScannerViewModel.kt` with CameraX preview, dynamic reticle box, gallery photo decoding fallback, and 5-step verification pipeline enforcing Ed25519 signature validation and TOFU public key pinning before establishing mutual trust.
- **Status**: RESOLVED

---

### [ISSUE-005] Group QR Code Payload Regenerates Every 1 Second
- **Severity**: P1 — High
- **Phase/Week**: Phase 1 — Week 8
- **Component**: `ui/group/GroupChatScreen.kt` (`QrInviteFullScreenModal`)
- **Environment**: All Android devices
- **Description**: The group invite QR code matrix re-renders and changes every second because `remember(groupId, remainingSeconds)` depends on the 1-second countdown timer. This makes camera scanning virtually impossible.
- **Root Cause**: `remember(groupId, remainingSeconds)` invalidates every second as `remainingSeconds` counts down, regenerating `GroupInviteCard` timestamp and signature payload.
- **Resolution**: Keyed `qrPayload` `remember` block strictly on `groupId` instead of `remainingSeconds`. The QR payload remains static for the 2-minute modal lifetime while the UI countdown badge continues updating.
- **Status**: RESOLVED

---

### [ISSUE-006] Broadcast Invite TCP Packets Undelivered to GroupManager
- **Severity**: P0 — Critical
- **Phase/Week**: Phase 1 — Week 8
- **Component**: `service/LanSocketManager.kt`, `ui/group/GroupChatViewModel.kt`
- **Environment**: All Android devices
- **Description**: Incoming group packets (`0x20`–`0x28`) are ignored by `LanSocketManager` because the packet type switch statement hits `else -> {}`. Broadcast invite notifications are never received or posted.
- **Root Cause**: `LanSocketManager.kt` lacks delegation to `GroupManager.handleGroupPacket()` for group packet types `GROUP_CREATE` through `GROUP_RESTORE`.
- **Resolution**: Added delegation in `LanSocketManager.kt` for packet types `0x20`–`0x28` to `groupManager.handleGroupPacket()`. Updated `GroupChatViewModel.kt`'s `broadcastInvite` to send `GROUP_CREATE` packets via `groupManager.broadcastGroupInvite()`.
- **Status**: RESOLVED

---

### [ISSUE-007] Missing Long-Press Option to Delete / Dissolve Group Chat
- **Severity**: P2 — Medium
- **Phase/Week**: Phase 1 — Week 8
- **Component**: `ui/peerlist/PeerListScreen.kt` (`GroupGlassListItem`)
- **Environment**: All Android devices
- **Description**: Long-pressing a group item on the peer list screen does not show a deletion dialog to remove or dissolve the group chat thread.
- **Root Cause**: `GroupGlassListItem` only implemented `clickable`, lacking `combinedClickable(onLongClick = ...)` and confirmation dialog logic.
- **Resolution**: Updated `GroupGlassListItem` to use `combinedClickable` with `onLongClick` handler. Added a confirmation `AlertDialog` ("Delete Group Chat?") in `PeerListScreen.kt` calling `GroupRepository.deleteGroup()`.
- **Status**: RESOLVED

---

### [ISSUE-008] Mutual TCP HELLO Handshake Infinite Loop
- **Severity**: P1 — High
- **Phase/Week**: Phase 2 — Week 12 (SIH Prototype)
- **Component**: `service/LanSocketManager.kt`
- **Environment**: Mode 2 Wi-Fi Direct and Mode 1 LAN TCP Handshake
- **Description**: Establishing a bidirectional TCP socket between client and Group Owner caused a circular flood of `HELLO` (`0x01`) packets because receiving a HELLO automatically triggered sending a HELLO packet back over the same socket indefinitely.
- **Root Cause**: Unconditional HELLO response emission without tracking whether a response had already been dispatched for that socket instance.
- **Resolution**: Added `private val helloResponsesSent = ConcurrentHashMap.newKeySet<Socket>()` in `LanSocketManager.kt`. When an outbound client socket sends a HELLO, or an inbound server socket replies with a HELLO, the socket is registered in `helloResponsesSent`. Subsequent HELLO packets on the same socket suppress duplicate replies.
- **Status**: RESOLVED

---

### [ISSUE-009] Android 13+ (API 33+) Wi-Fi Direct Runtime Permission Gating
- **Severity**: P0 — Critical
- **Phase/Week**: Phase 2 — Week 12 (SIH Prototype)
- **Component**: `MainActivity.kt`, `service/WifiDirectController.kt`
- **Environment**: Android 13+ (API 33+, Tiramisu) Devices
- **Description**: On Android 13+ devices, Wi-Fi Direct peer discovery failed silently or threw `SecurityException` when attempting to discover peers or request peer lists because only legacy `ACCESS_FINE_LOCATION` was requested.
- **Root Cause**: Android 13 introduced `Manifest.permission.NEARBY_WIFI_DEVICES` which is strictly required for Wi-Fi Direct operations.
- **Resolution**: Added version checks (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`) in `MainActivity.kt` and `WifiDirectController.kt` to request and verify `NEARBY_WIFI_DEVICES` on API 33+ (and `ACCESS_FINE_LOCATION` on API < 33). Added a reactive `_networkPermissionsGranted` StateFlow in `MainActivity` to defer starting foreground network services until permission is confirmed.
### [ISSUE-010] Chat Database Cross-Talk & Shared In-Memory Messages Across Peers
- **Severity**: P0 — Critical
- **Phase/Week**: Phase 2 — Week 12
- **Component**: `ui/navigation/NavGraph.kt`, `ui/chat/ChatViewModel.kt`, `service/DiscoveryService.kt`
- **Environment**: Multi-Device LAN / Android Client
- **Description**: Messages sent to or received from Peer A were displayed inside Peer B's chat room.
- **Root Cause**: (1) `ChatViewModel` in `NavGraph` was not keyed by `peerId` in Compose Navigation. (2) `ChatViewModel.loadPeerMessages()` merged existing in-memory messages with the newly loaded historical messages without purging the previous peer's state. (3) mDNS discovery used `HyperShare_Username` while UDP/TCP used `peer-uuid`, creating split identities.
- **Resolution**: Keyed `viewModel(key = key.peerId)` in `NavGraph.kt`, immediately reset `ChatUiState` with `messages = emptyList()` upon opening a chat, and synchronized mDNS/UDP identity using cryptographic `peerId`.
- **Status**: RESOLVED

### [ISSUE-011] Emergency Mode Missing Ever-Connected Device History for Multi-Hop Mesh
- **Severity**: P1 — High
- **Phase/Week**: Phase 2 — Week 12
- **Component**: `ui/peerlist/PeerListViewModel.kt`, `ui/peerlist/PeerListScreen.kt`, `db/MessageRepository.kt`
- **Environment**: All Android Devices
- **Description**: Switching to Emergency Mode (Mode 2) only displayed devices currently visible in live Wi-Fi Direct scans. Because multi-hop mesh routing forwards packets across known destination peers, users could not select previously connected peers to compose or queue messages.
- **Root Cause**: Peer list view state in Mode 2 was hard-filtered to only the instantaneous Wi-Fi Direct scan list rather than unifying known persistent peer conversations.
- **Resolution**: Added `getAllConversations()` query in `MessageRepository.kt` and integrated conversation history in `PeerListViewModel.kt` to present all historical peer conversations in both Local and Emergency modes with live presence overlays.
- **Status**: RESOLVED

### [ISSUE-012] Local Mode Discovery Card Jitter & Scanning Instability
- **Severity**: P2 — Medium
- **Phase/Week**: Phase 2 — Week 12
- **Component**: `ui/peerlist/PeerListViewModel.kt`, `ui/peerlist/PeerListScreen.kt`
- **Environment**: All Android Devices
- **Description**: Continuous background network scanning caused peer cards to constantly reorder and jump around the user's screen.
- **Root Cause**: Incoming discovery events were appended/mutated dynamically without stable timestamp-based ordering.
- **Resolution**: Re-architected conversation list to sort stably by latest activity/message timestamp (`lastActivity DESC`, WhatsApp-style) and highlighted active LAN peers with a distinct green outline (`BorderStroke(1.5.dp, ConnectedGreen)`) while removing redundant duplicate status chips and avatar rings.
- **Status**: RESOLVED

---

## 3. Bug Report Template (Copy for new issues)

```markdown
### [ISSUE-XXX] Short Title
- **Severity**: P0 / P1 / P2 / P3
- **Phase/Week**: Phase X — Week Y
- **Component**: `layer/ComponentName`
- **Environment**: Device Model, Android OS Version, API Level
- **Description**: Detailed description of what occurred vs expected behavior.
- **Steps to Reproduce**:
  1. Step 1
  2. Step 2
  3. Step 3
- **Root Cause**: Technical analysis of why the bug occurred.
- **Resolution**: Description of the fix, PR link, or mitigation strategy.
- **Status**: [OPEN / IN_PROGRESS / RESOLVED / WONT_FIX]
```
