# HyperShare — File Structure & AI Agent Context Index

> **AI AGENT INSTRUCTION**: This document is an index for AI coding assistants to understand the layout, responsibilities, component relationships, and change log of the HyperShare project.  
> **MANDATORY RULE**: Every time you perform any file operation (creating, modifying, refactoring, or deleting files), you **MUST** update this file to reflect the change and append an entry to the [Change Log](#change-log).

---

## 1. Directory Tree Overview

```
HyperShare/
├── README.md                              # Main project overview & high-level documentation
├── Filestructure.md                        # [THIS FILE] AI Agent file map & change tracking log
├── docs/                                  # Project specifications & technical reference docs
│   ├── Architecture.md                    # High-level architecture & module breakdown
│   ├── Bug_Log.md                         # Production bugs, edge cases, SLA severity register
│   ├── Mesh_Routing_Logic.md              # AODV-inspired routing logic & GO election scoring
│   ├── Protocol_Specs.md                  # Binary packet formats, headers, and control codes
│   ├── Running_Steps.md                   # Physical device setup & execution guide
│   ├── Testing_Scenarios.md               # Test cases for Mode 1 & Mode 2 mesh topologies
│   ├── Theme.md                           # UI design system, colors, glassmorphism, typography
│   └── Workflow.md                        # Week-by-week development plan & phase breakdown
├── build.gradle.kts                       # Root Gradle build script
├── settings.gradle.kts                    # Gradle settings & plugin management
├── gradle.properties                      # JVM & Android build properties
├── gradle/
│   └── libs.versions.toml                 # Version catalog (Compose BOM, Kotlin, Coroutines)
└── app/
    ├── build.gradle.kts                   # Application module build config (namespace: com.hypershare)
    └── src/
        └── main/
            ├── AndroidManifest.xml        # Permissions (WiFi Direct, mDNS, Storage) & Service registration
            └── java/
                └── com/
                    └── hypershare/
                        ├── MainActivity.kt                      # Edge-to-edge Compose host activity
                        ├── application/                         # App-wide singletons & state management
                        │   ├── HyperShareApplication.kt         # Application entry point
                        │   ├── SessionManager.kt                # Peer state & event flow arbitrator
                        │   ├── TransferQueue.kt                 # Priority queue for outbound chunks
                        │   └── StreamController.kt              # In-memory ring-buffer for video/audio
                        ├── model/                               # Data domain models
                        │   ├── ConnectedPeer.kt                 # Peer entity & status enum
                        │   ├── RouteEntry.kt                    # AODV routing table entry
                        │   ├── DataChunk.kt                     # 64 KB chunk + CRC32 payload
                        │   ├── TransferJob.kt                   # File/stream transfer task model
                        │   ├── Packet.kt                        # Binary packet header & payload model
                        │   └── SessionEvent.kt                  # SharedFlow events (PeerJoined, etc.)
                        ├── security/                            # Crypto & Key Management
                        │   ├── EphemeralKeyPair.kt              # ECDH (Curve25519) key generation
                        │   ├── SessionEncryptor.kt              # AES-256-GCM encryption/decryption
                        │   ├── PeerKeyStore.kt                  # Public key storage cache
                        │   └── TofuManager.kt                   # Trust-On-First-Use verification
                        ├── protocol/                            # Binary Protocol & Chunking
                        │   ├── PacketBuilder.kt                 # Packet construction & serialization
                        │   ├── PacketParser.kt                  # ByteArray deserializer
                        │   ├── ChunkManager.kt                  # 64 KB splitting & reassembly with CRC32
                        │   └── ControlPacketHandler.kt          # HELLO, ACK, RREQ/RREP parser
                        ├── routing/                             # Mesh Routing Engine
                        │   ├── RoutingTable.kt                  # ConcurrentHashMap route table
                        │   ├── HopManager.kt                    # TTL decrement & relay decisions
                        │   ├── GroupOwnerElection.kt            # WiFi Direct GO scoring algorithm
                        │   └── ModeController.kt                # Mode 1 <-> Mode 2 auto transition
                        ├── service/                             # Android Foreground Services
                        │   ├── MeshNetworkService.kt            # ServerSocket (Port 47200) & P2P GO
                        │   ├── DiscoveryService.kt              # mDNS (_hypershare._tcp) & P2P scan
                        │   └── TransferService.kt               # File I/O & chunk transfer loop
                        ├── di/                                  # Dependency Injection
                        │   ├── AppModule.kt                     # Application-scoped providers
                        │   └── NetworkModule.kt                 # Network & routing providers
                        └── ui/                                  # Jetpack Compose UI Layer
                            ├── theme/                           # Design tokens (OLED dark theme)
                            │   ├── Color.kt                     # Signal Blue (#3B82F6), Mesh Teal (#14B8A6)
                            │   ├── Type.kt                      # Monospace captions & Inter typography
                            │   └── Theme.kt                     # HyperShareTheme dark color scheme
                            ├── components/                      # Reusable Compose UI elements
                            │   ├── GlassCard.kt                 # Frosted 5% white glass overlay modifier
                            │   ├── GroupAvatarStack.kt           # Stacked overlapping avatar circles with deterministic colors
                            │   ├── StatusChip.kt                # State indicator chip (CONNECTED, RELAY)
                            │   ├── QrCodeCard.kt                # Real ZXing QR Code matrix renderer
                            │   └── QrCodeGenerator.kt           # ZXing BitMatrix encoder utility
                            ├── navigation/                      # Navigation Graph
                            │   └── NavGraph.kt                  # NavDisplay host & screen routes
                            ├── peerlist/                        # Peer List Screen
                            │   ├── PeerListScreen.kt            # Live peer list UI with FAB mode switch
                            │   └── PeerListViewModel.kt         # PeerListUiState & mode toggle
                            ├── chat/                            # Encrypted Chat Screen
                            │   ├── ChatScreen.kt                # Per-peer messaging bubble UI
                            │   └── ChatViewModel.kt             # Message thread state
                            ├── filebrowser/                     # File Transfer Browser Screen
                            │   ├── FileBrowserScreen.kt         # File picker & VIEW_ONLY toggle UI
                            │   └── FileBrowserViewModel.kt      # Transfer queue state
                            ├── stream/                          # In-Memory Stream Player Screen
                            │   ├── StreamPlayerScreen.kt        # View-only video player UI
                            │   └── StreamPlayerViewModel.kt     # Streaming ring-buffer state
                            ├── modetoggle/                      # Network Mode Switcher Modal
                            │   ├── ModeToggleScreen.kt          # Mode 1 vs Mode 2 selector card UI
                            │   └── ModeToggleViewModel.kt       # Selected mode state
                            ├── routingdebug/                    # Dev Routing Visualizer Screen
                            │   ├── RoutingDebugScreen.kt        # Mesh topology visualizer UI
                            │   └── RoutingDebugViewModel.kt     # Routing table debug state
                            ├── group/                           # Group Communication Screens
                            │   ├── GroupListScreen.kt            # Group list with FAB creation dialog
                            │   ├── GroupListViewModel.kt         # Group list state management
                            │   ├── GroupChatScreen.kt            # Group chat UI with admin menu & invite flows
                            │   ├── GroupChatViewModel.kt         # Group chat state, invite, and messaging logic
                            │   ├── GroupBottomSheets.kt          # CreateGroup, DirectInvite, GroupJoin bottom sheets
                            │   ├── QrCodeScannerDialog.kt        # Full-screen QR Scanner Dialog with 5-step pipeline & corner brackets
                            │   ├── GroupInviteActionReceiver.kt  # BroadcastReceiver for notification Join/Decline actions
                            │   └── GroupJoinRequestReceiver.kt   # BroadcastReceiver for admin join request approval/decline
                            └── qrscanner/                       # QR Code Scanner & Identity Exchange Screen
                                ├── QrScannerScreen.kt           # CameraX scanner, reticle overlay, gallery image picker & paste dialog
                                └── QrScannerViewModel.kt        # QR frame decoding, gallery photo decoding & Ed25519 signature verification
```

---

## 2. File Responsibilities & Context Index

### Core Specifications (`docs/`)
- `docs/Architecture.md`: Master architectural blueprint (Layers: UI -> Application -> Service -> Routing -> Protocol -> Security -> Hardware).
- `docs/Bug_Log.md`: Production bugs, edge cases, SLA severity standards, and issue register.
- `docs/Mesh_Routing_Logic.md`: Routing specifications (AODV, RREQ, RREP, RERR, GO Election algorithm).
- `docs/Protocol_Specs.md`: Binary protocol specification (1 byte Version, 1 byte Type, 1 byte TTL, 4 byte SeqNum, etc.).
- `docs/Running_Steps.md`: Device deployment, ADB connection, and app testing instructions.
- `docs/Theme.md`: Design system rules (Dark OLED `#0A0A0F`, Glass Card overlays, Status chips, JetBrains Mono font).
- `docs/Workflow.md`: 24-week development roadmap.

### Android Application Layer (`app/src/main/java/com/hypershare/`)
- `MainActivity.kt`: Entry Activity enabling edge-to-edge layout and mounting `HyperShareNavGraph()`.
- `application/SessionManager.kt`: Global state holder exposing `StateFlow<List<ConnectedPeer>>` and `SharedFlow<SessionEvent>`.
- `application/TransferQueue.kt`: Outbound transfer priority queue (`CONTROL > DISASTER_MSG > FILE > STREAM`).
- `application/StreamController.kt`: Ring-buffered in-memory byte stream for `VIEW_ONLY` permissions.
- `security/SessionEncryptor.kt`: AES-256-GCM encryption wrapper with random 12-byte IV per packet.
- `security/EphemeralKeyPair.kt`: KeyPairGenerator ("EC", 256-bit) and ECDH shared secret derivation.
- `security/PeerKeyStore.kt`: Public key storage cache.
- `security/TofuManager.kt`: Trust-On-First-Use verification.
- `protocol/ChunkManager.kt`: Splits files into 64 KB chunks and verifies CRC32 on reassembly.
- `protocol/PacketBuilder.kt` & `PacketParser.kt`: Handles binary serialization and parsing of custom network packets.
- `routing/RoutingTable.kt`: Manages `ConcurrentHashMap<String, RouteEntry>` with stale route pruning.
- `routing/HopManager.kt`: Validates destination, decrements TTL, and makes `DeliverLocal` vs `Relay` decisions.
- `model/PeerDiscoveryEvent.kt`: Sealed class representing `PeerDiscovered(peer)` and `PeerLost(peerId)` mDNS events.
- `service/MeshNetworkService.kt`: Foreground service listening on TCP port `47200` (Mode 1) & managing WifiP2p (Mode 2).
- `service/DiscoveryService.kt`: Foreground service running `NsdManager` mDNS service registration (`_hypershare._tcp.`), resolution, and discovery event emission.
- `ui/theme/Color.kt`: Color constants (`SignalBlue`, `MeshTeal`, `BackgroundBase`, `ConnectedGreen`, etc.).
- `ui/components/GlassCard.kt`: Custom Modifier `.glassCard()` applying 5% white overlay and 8% border.
- `ui/components/BottomNavBar.kt`: 3-tab bottom navigation bar (Home/Share, Peers & Chats, Scan ID) + Settings link.
- `ui/components/QrCodeCard.kt`: QR Code grid component displaying "Share ID".
- `ui/home/HomeScreen.kt` & `HomeViewModel.kt`: Screen 1 UI (HYPERSHARE banner, LOCAL MODE Green / EMERGENCY MODE Red cards, QR Share ID card).
- `ui/peerlist/PeerListScreen.kt`: Screen 2 UI (HYPERSHARE banner, grey list cards, status subtext `__NEW_MESSAGE__` Green / `__SENT_MESSAGE__` Red).
- `ui/chat/ChatScreen.kt`: Screen 3 UI (Header with avatar & 3-dots, `__DATE_OF_CONVERSATION__` divider, Blue outbound bubbles on right, Green inbound bubbles on left, bottom input bar with Camera + Attachment icons).
- `ui/settings/AccountSettingsScreen.kt` & `AccountSettingsViewModel.kt`: Account profile settings screen (User display name, Peer Node ID, ECDH key fingerprint, QR ID badge).
- `ui/settings/AppSettingsScreen.kt` & `AppSettingsViewModel.kt`: App configuration screen (Dark/Light mode preference, auto-switch emergency mode toggle, security playground link).
- `ui/components/GroupAvatarStack.kt`: Stacked overlapping circular avatars component with deterministic color assignment from `userId.hashCode()` and `+N` overflow badge.
- `ui/group/GroupListViewModel.kt` & `GroupChatViewModel.kt`: Group list management and group chat messaging / member management state ViewModels.
- `ui/group/GroupChatScreen.kt`: Group chat UI with message bubbles, GroupAvatarStack header, three-dot admin menu (Broadcast Invite, QR Invite, Invite Directly), QR invite full-screen modal, and admin join request banners.
- `ui/group/GroupBottomSheets.kt`: Bottom sheet composables for group creation (member selection, segmented type control), direct peer invitation, and incoming group join requests with provenance badges (`QR`, `BROADCAST`, `DIRECT`).
- `ui/group/QrCodeScannerDialog.kt`: Full-screen camera scanner dialog with dark scrim `#0A0A0F`, 260x260dp viewport, pulsing corner brackets (`MeshTeal`), horizontal scan line (`SignalBlue`), 5-step verification pipeline (Ed25519 signature hard stop), and `PostScanJoinConfirmationView`.
- `ui/group/GroupInviteActionReceiver.kt` & `GroupJoinRequestReceiver.kt`: `BroadcastReceiver` implementations (`exported="false"`) handling notification inline actions for broadcast invites and admin join request approvals.
- `model/GroupInviteCard.kt`: Cryptographically signed group invite model with `Ed25519` signature verification and JSON serialization.
- `ui/navigation/NavGraph.kt`: Type-safe navigation entries for all app screens including `AccountSettingsNavKey`, `AppSettingsNavKey`, `GroupListNavKey`, and `GroupChatNavKey`.
- `ui/testing/SecurityPlaygroundScreen.kt` & `SecurityPlaygroundViewModel.kt`: Interactive Phase 0 test harness UI for ECDH agreement, AES-256-GCM encryption/decryption, binary packet serialization, and TOFU key store verification.
- `db/GroupRepository.kt`: SQLite database repository for `Group`, `GroupMember`, and `GroupMessage` entities.
- `model/GroupModels.kt`: Data models and serializable packet DTOs for multi-peer groups (`Group`, `GroupMember`, `GroupMessage`, `GroupCreatePayload`, etc.).
- `application/GroupManager.kt`: Core group state machine, admin access control, O(N) direct message distribution, temporary group lifecycle tracking, and reconnection restore manager.

### Unit Tests (`app/src/test/java/com/hypershare/`)
- `security/EphemeralKeyPairTest.kt`: Unit tests verifying EC 256-bit key pair generation & ECDH shared secret symmetry across two peers.
- `security/SessionEncryptorTest.kt`: Unit tests verifying AES-256-GCM encryption/decryption roundtrip, unique IV headers, and payload tamper detection.
- `security/PeerKeyStoreTest.kt`: Unit tests for public key caching, retrieval, and removal.
- `security/TofuManagerTest.kt`: Unit tests for Trust-On-First-Use key acceptance, identity verification, and MITM alteration rejection.
- `service/PeerDiscoveryEventTest.kt`: Unit tests for mDNS peer discovered/lost events and `PeerListViewModel` state updates.
- `protocol/GroupPacketSerializationTest.kt`: Unit tests verifying serialization, parsing, and enum mapping for group packet types `0x20`–`0x28`.

---

## 3. Change Log

| Timestamp | Developer / AI | Action | Description |
|---|---|---|---|
| 2026-07-25 | AI Agent | Initial Project Architecture Setup | Created initial Android Gradle project, package structure (`com.hypershare.*`), and stubbed all 9 architecture layers. |
| 2026-07-25 | AI Agent | Documentation Re-organization | Moved specification `.md` files (`Architecture.md`, `Mesh_Routing_Logic.md`, `Protocol_Specs.md`, `Testing_Scenarios.md`, `Theme.md`, `Workflow.md`) into `docs/` folder in root. Created `Filestructure.md`. |
| 2026-08-06 | AI Agent | Added Production Bug Log | Created `docs/Bug_Log.md` register with severity SLAs and pre-populated initial setup/permission edge cases. Updated `Filestructure.md`. |
| 2026-08-06 | AI Agent | Phase 0 Week 3 Security Verification | Implemented and verified unit tests (`EphemeralKeyPairTest`, `SessionEncryptorTest`, `PeerKeyStoreTest`, `TofuManagerTest`) passing with 100% success (`BUILD SUCCESSFUL`). Marked Week 3 completed in `Workflow.md`. |
| 2026-08-06 | AI Agent | Security & Protocol Testing UI | Built interactive `SecurityPlaygroundScreen` & `SecurityPlaygroundViewModel` for live ECDH, AES-256-GCM, packet serialization, and status chip testing. Verified debug APK build (`BUILD SUCCESSFUL`). |
| 2026-08-06 | User / AI | Added Running Steps Guide | Created `docs/Running_Steps.md` documenting device connection steps, Gradle installation, and app execution procedures. |
| 2026-08-06 | AI Agent | Wireframe UI Redesign | Built 3-screen layout matching `UI basic structure.png` (`HomeScreen`, `PeerListScreen`, `ChatScreen`, `BottomNavBar`, `QrCodeCard`). Verified live execution on phone via ADB. |
| 2026-08-06 | AI Agent | Resolved UI Issues & Navigation | Fixed status bar inset padding across all screens, added circular Send button in ChatScreen, wired Mode cards to LAN vs Mesh peer filters, built `AccountSettingsScreen` (👤) & `AppSettingsScreen` (⚙), made top HYPERSHARE title return home, and logged ISSUE-004 in `Bug_Log.md`. |
| 2026-08-06 | AI Agent | Fixed Chat UI Dynamic Keyboard Shrinking | Added `android:windowSoftInputMode="adjustResize"` to `<activity>` in `AndroidManifest.xml` and refactored `ChatScreen.kt` to a single root Column layout. Fixed top header pinning and dynamic `LazyColumn` weight shrinking when soft keyboard opens. Verified live on phone via ADB. |
| 2026-08-06 | AI Agent | Phase 1 Week 4 mDNS Discovery | Implemented live mDNS local LAN service registration & discovery (`_hypershare._tcp.`) in `DiscoveryService.kt`, created `PeerDiscoveryEvent.kt`, updated `PeerListViewModel` & `PeerListScreen`, and verified unit tests (`PeerDiscoveryEventTest`) passing (`BUILD SUCCESSFUL`). Marked Phase 1 Week 4 completed in `Workflow.md`. |
| 2026-08-06 | AI Agent | Applied Dark Glassmorphism UI Theme | Implemented full OLED space black `#0A0A0F` base theme, `AmbientMeshGraphCanvas` topology background, `GlassCard` 5% overlay + 8% border modifier, `StatusChip` state badges with Mesh pulse animation, and monospace metadata formatting per `docs/Theme.md`. Verified live execution on phone via ADB. |
| 2026-08-06 | AI Agent | Fixed Mode Selector Cards Styling | Added exact `RoundedCornerShape(16.dp)` clipping to `ModeCardButton` overlay in `HomeScreen.kt` to prevent corner bleeding, and updated `EMERGENCY` mode card to an `ErrorRed` tone with 1.5dp selection border. |
| 2026-08-06 | AI Agent | Implemented Local Device Identity & SQLite Chat DB | Built `UserIdentityManager.kt` for KeyStore/SharedPreferences per-device username & peer ID persistence, created `ChatDatabaseHelper.kt` & `MessageRepository.kt` SQLite database layer, updated `ChatViewModel` & `AccountSettingsScreen`, and verified unit tests (`MessageRepositoryTest`) passing (`BUILD SUCCESSFUL`). |
| 2026-08-06 | AI Agent | Documented Xiaomi USB Install Fix | Updated `docs/Running_Steps.md` with step-by-step instructions to resolve `INSTALL_FAILED_USER_RESTRICTED` security prompt on Xiaomi/Redmi/POCO devices. |
| 2026-08-06 | AI Agent | Phase 1 Weeks 5 & 6 Encrypted Texting | Implemented `LanSocketManager.kt` for TCP ServerSocket on port `47200` & client connections, integrated binary HELLO (`0x01`) and MSG (`0x02`) packets, wired `ChatViewModel` to `LanSocketManager`, and verified unit tests (`LanSocketManagerTest`) passing (`BUILD SUCCESSFUL`). Marked Weeks 5 & 6 completed in `Workflow.md`. |
| 2026-08-06 | AI Agent | LAN Discovery, Persistence & UI Refinement | (1) Dual mDNS & UDP Subnet Broadcast (`47201`) discovery with MulticastLock & IP deduplication in `DiscoveryService.kt` & `PeerListViewModel`. (2) `LanSocketManager` converted to thread-safe Singleton (`EADDRINUSE` fix) with `DataOutputStream` caching per IP. (3) `UserIdentityManager` persistent device identity, `MessageRepository` SQLite database persistence layer, and clear/restore chat room options. |
| 2026-08-07 | AI Agent | Crash Fixes & UI System Refinements | (1) Fixed unsigned byte parsing in `PacketParser.kt` (`and 0xFFFF`) preventing packet `NegativeArraySizeException` crashes. (2) Changed message IDs to `UUID.randomUUID()` with list deduplication, fixing Compose `LazyColumn` duplicate key crashes. (3) System Notifications with high-priority channel and `activeChatPeerId` notification suppression when viewing active chat. (4) Dynamic timestamps (`hh:mm a`), ambient graph canvas, clear local chat options menu, and non-navigational profile header. |
| 2026-08-07 | AI Agent | TCP Protocol Engine & 3-Stage Read-ACK System Overhaul | (1) **Bidirectional Socket Engine**: Enabled full two-way communication by launching `handleIncomingSocket` on outbound client sockets. (2) **Framing & Race Protection**: Synchronized `DataOutputStream` writes (`synchronized(out)`), eliminating TCP frame length corruption (`0`). Consolidated ACK emission to single targeted ACK (`READ_ACK` vs `ACK`) to remove dual-ACK races. (3) **Reliable Connections**: Removed 10s idle `soTimeout` and enabled TCP `keepAlive = true`. Purged stale socket map entries on disconnections. (4) **Chat Order & Read-ACK Dispatch**: Changed flow `replay=0` and sorted all message merges by timestamp. Postponed `markMessagesAsRead()` until SQLite DB load completes, ensuring 100% reliable 2 Green Tick (`✓✓`) status delivery upon opening unread chats. |
| 2026-08-07 | AI Agent | Notification Deep Link Chatroom Navigation | Added `OPEN_PEER_ID` intent extra to notification `PendingIntent` in `LanSocketManager.kt`, captured in `MainActivity.kt` (`onCreate` & `onNewIntent`), and added `LaunchedEffect(targetPeerId)` deep-link handler in `NavGraph.kt` so tapping any message notification opens the exact peer chatroom directly instead of the home screen. |
| 2026-08-07 | AI Agent | Direct Background SQLite Persistence | Updated `LanSocketManager.kt` to immediately persist all incoming `MSG`, `ACK`, and `READ_ACK` packets directly to SQLite DB (`MessageRepository`) in the background. Incoming background messages are no longer dependent on `ChatViewModel` being active, guaranteeing zero message loss when app is closed/in background and enabling instant display when tapping notifications. |
| 2026-08-07 | AI Agent | Contact-Gated Trust & Group Architecture Docs Update | Integrated **Idea 1 (Contact-Gated Mutual Trust)** and **Idea 2 (Permanent & Temporary Group Messaging)** across documentation: (1) `docs/Workflow.md` updated with Week 7 (Contact-Gated Trust Engine) and Week 8 (Groups Engine) milestones and re-aligned week timeline. (2) `docs/Protocol_Specs.md` updated with Group Packet Types `0x20`–`0x28`, Section 10 (Contact-Gated Trust Specification), and Section 11 (Group Management Protocol). (3) `docs/Architecture.md` updated with `ContactManager` and `GroupManager` in Application Layer. |
| 2026-08-08 | AI Agent | Device-Stable Cryptographic Identity | Added BouncyCastle dependency and built `IdentityManager.kt` (`stableDeviceUUID` v4 + KeyStore salt + `BLAKE2b-256` UserID + `Ed25519` keypair), built `ContactCard.kt` signature model, refactored `UserIdentityManager.kt`, and added `IdentityManagerTest.kt` unit tests. |
| 2026-08-08 | AI Agent | QR Code Sharing & Camera/Gallery Scanner Engine | (1) Added ZXing core & CameraX dependencies and `CAMERA` permission. (2) Built `QrCodeGenerator.kt` for real 2D matrix encoding and updated `QrCodeCard.kt` to render real QR matrices on Compose Canvas. (3) Built `QrScannerScreen.kt` & `QrScannerViewModel.kt` with CameraX preview, reticle overlay, gallery image picker (`decodeGalleryPhoto`), manual paste fallback, and Ed25519 signature verification alert. (4) Wired `QrScannerNavKey` into `NavGraph.kt` and `BottomNavBar.kt`. |
| 2026-08-08 | AI Agent | Contact-Gated Mutual Peer Trust Protocol & Gating Engine | (1) Upgraded SQLite `ChatDatabaseHelper.kt` to `DATABASE_VERSION = 3` with `is_trusted`, `has_peer_accepted`, and `public_key` columns in `peers` table. (2) Added `CONTACT_ACCEPT` (`0x0A`) packet type and `sendContactAcceptPacket` / `contactAcceptEventsFlow` in `LanSocketManager.kt`. (3) Enforced strict mutual gating (`isPeerTrusted && hasPeerAcceptedUs`) in `ChatViewModel.kt` restricting un-paired peers to 2 preview messages (300 chars max) until BOTH users accept contact. (4) Updated `ChatScreen.kt` with dynamic state machine banner (*"Awaiting Peer Acceptance"* vs *"Contact Accepted by Peer"*). |
| 2026-08-08 | AI Agent | Peer Display Names, Unread Badges, Self-Discovery Fix & Week 7 Sign-off | (1) Added `peers` DB display name resolution in `MessageRepository.kt`, `ChatViewModel.kt`, and notifications. (2) Added `getUnreadMessageCountForPeer`, `getTotalUnreadMessageCount`, and `getLastMessageForPeer` queries rendering WhatsApp-style circular pill badges (`1`, `2`, `99+`), last chat snippets, and timestamps across `PeerListScreen.kt` and `HomeScreen.kt`. (3) Cleaned up emoji characters for clean typography per directive. (4) Added `isLocalDeviceAddress` in `DiscoveryService.kt` filtering self-IPs from mDNS and UDP discovery. (5) Added Developer Mode app data wiping in `AppSettingsScreen.kt` and signed off Week 7 in `docs/Workflow.md`. |
| 2026-08-13 | AI Agent | Phase 1 Week 8 Group Communication Engine | Implemented Week 8 Permanent & Temporary Group Communication: (1) Updated `docs/Protocol_Specs.md` and `docs/Workflow.md` for consistent group packet payload specifications. (2) Added Group packet types `0x20`–`0x28` to `PacketType` enum and `PacketBuilder.kt`. (3) Created `GroupModels.kt`, upgraded `ChatDatabaseHelper.kt` (`DATABASE_VERSION = 4` with `groups`, `group_members`, and `group_messages` tables), and created `GroupRepository.kt`. (4) Built `GroupManager.kt` handling admin group creation, O(N) direct message distribution, member add/remove/kick, temporary group lifecycle dissolve, and reconnect restore triggers. (5) Created `GroupListViewModel.kt` & `GroupChatViewModel.kt` and verified unit tests (`GroupPacketSerializationTest`) passing (`BUILD SUCCESSFUL`). |
| 2026-08-13 | AI Agent | Group List & Creation UI Integration | Created `GroupListScreen.kt` featuring creation dialogs for Permanent and Temporary groups, added a `GROUPS` header button to `PeerListScreen.kt`, and registered `GroupListNavKey` in `NavGraph.kt`. |
| 2026-08-13 | AI Agent | Group Creation FAB, Bottom Sheets & Invite Flow | (1) Created `GroupAvatarStack.kt` with deterministic 6-color palette from `userId.hashCode()`, stacked overlapping circles, and `+N` overflow badge. (2) Built `GroupBottomSheets.kt` with `CreateGroupBottomSheet` (group name, segmented type control, member selection checkboxes), `DirectInviteBottomSheet` (per-peer invite buttons), and `GroupJoinBottomSheet` (non-dismissible join/decline). (3) Built `GroupChatScreen.kt` with message bubbles, sender-colored names, three-dot admin menu (Broadcast Invite, QR Invite, Invite Directly), and `QrInviteFullScreenModal` with countdown timer. (4) Updated `GroupChatViewModel.kt` with `broadcastInvite()` and `invitePeerDirectly()` methods. (5) Updated `PeerListScreen.kt` with `group_add` FAB, `CreateGroupBottomSheet` wiring, and inline `GroupGlassListItem` entries below peers. (6) Added `GroupChatNavKey` to `NavGraph.kt` and wired full group chat navigation. |
| 2026-08-13 | AI Agent | Group QR Code Scanner, Broadcast Invites & Admin Join Requests | (1) Upgraded SQLite `ChatDatabaseHelper.kt` to `DATABASE_VERSION = 5` with `join_method` migration script (`ALTER TABLE groups ADD COLUMN join_method TEXT NOT NULL DEFAULT 'DIRECT'`). (2) Created `GroupInviteCard.kt` with Ed25519 signature payload construction and verification logic. (3) Created `QrCodeScannerDialog.kt` full-screen Dialog with 260x260dp cutout, pulsing Mesh Teal corner brackets, Signal Blue scan line, camera permission handling (`AlertDialog` & settings snackbar), and 5-step verification pipeline (hard stop on Ed25519 signature failure). (4) Added `GroupInviteActionReceiver.kt` and `GroupJoinRequestReceiver.kt` (`exported="false"`) handling notification inline actions. (5) Added foreground in-app `BroadcastInviteBannerStack` on `PeerListScreen` with "Broadcast Invite" chip (`WarningAmber #F59E0B`) and 30s auto-dismiss. (6) Added admin-side QR join request banners and `PendingJoinRequest` state in `GroupChatScreen.kt` & `GroupChatViewModel.kt`. |
| 2026-08-15 | AI Agent | Chatroom Theme Unification, Username Resolution & Demo Removal | (1) Replaced hardcoded 'Me' display names in `GroupManager.kt` with real usernames from `UserIdentityManager`. (2) Fixed `GroupChatScreen.kt` sender display name resolution so incoming messages display real usernames (never 'Me'). (3) Unified chatroom theme across 1-to-1 `ChatScreen` and `GroupChatScreen` (bubbles, headers, input bars). (4) Completely removed demo chat rooms (`peer-1`, `peer-2`, 'Demo Chat Rooms') and UI toggles from `PeerListScreen` & `PeerListViewModel`. (5) Consolidated duplicate creation buttons (`+ CREATE` in `GroupListScreen` header removed in favor of FAB). |
| 2026-08-15 | AI Agent | Top Bar Button Cleanup & Universal Bottom Scanner | (1) Removed duplicate 📷 camera button and GROUPS button from `PeerListScreen` top header card, leaving single clean ↻ refresh button. (2) Upgraded `QrScannerViewModel.kt` with universal QR decoding supporting both Group Invite QR payloads and Contact Exchange QR payloads via single bottom navigation scan button. |
| 2026-08-15 | AI Agent | Group 3-Stage Tick Verification & Industry Notification System | (1) Added `GROUP_MSG_READ` (`0x29`) packet type and upgraded database to `DATABASE_VERSION = 6` with `group_message_receipts` table. (2) Implemented Group 3-stage tick progression: 1 Grey Tick (`✓`) = Sent, 2 Grey Ticks (`✓✓`) = Delivered to all members, 2 Green Ticks (`✓✓`) = Read/viewed by all members. (3) Built high-priority status bar notification channel `"hypershare_group_messages"` with heads-up banners and deep-link intent navigation (`OPEN_GROUP_ID`). (4) Added per-group unread counters and circular pill badges (`1`, `2`, `99+`) on `PeerListScreen.kt`. |
| 2026-08-15 | AI Agent | UI & Backend Latency Optimizations | (1) Optimized `AmbientMeshGraphCanvas.kt` with primitive float array recycling to eliminate frame object allocations for butter-smooth 60-120 FPS animation. (2) Created `TimeFormatter.kt` singleton using `ThreadLocal<SimpleDateFormat>` for zero-allocation date formatting in message bubbles across `ChatScreen.kt` and `GroupChatScreen.kt`. (3) Offloaded SQLite unread count queries to `Dispatchers.IO` in `PeerListScreen.kt` to prevent UI thread layout composition lag. (4) Added `BufferedOutputStream` wrapping to TCP streams in `LanSocketManager.kt` for faster packet throughput. |





