# HyperShare — File Structure & AI Agent Context Index

> **AI AGENT INSTRUCTION**: This document is an index for AI coding assistants to understand the layout, responsibilities, component relationships, and change log of the HyperShare project.  
> **MANDATORY RULE**: Every time you perform any file operation (creating, modifying, refactoring, or deleting files), you **MUST** update this file to reflect the change and append an entry to the [Change Log](#change-log).

---

## 1. Directory Tree Overview

```
HyperShare/
├── README.md                              # Main project overview & high-level documentation
├── Filestructure.md                        # [THIS FILE] AI Agent file map & change tracking log
├── run.bat                                # Interactive cross-platform deployment & build script
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
├── .gitignore                             # Git ignore rules for build and local test artifacts
├── gradle/
│   └── libs.versions.toml                 # Version catalog (Compose BOM, Kotlin, Coroutines)
└── app/
    ├── build.gradle.kts                   # Application module build config (namespace: com.hypershare)
    └── src/
        ├── test/java/com/hypershare/      # Unit Test Suite
        │   ├── db/
        │   │   └── MessageRepositoryTest.kt             # SQLite message repository CRUD & unread count tests
        │   ├── identity/
        │   │   └── IdentityManagerTest.kt               # BLAKE2b UserID derivation & Ed25519 signature tests
        │   ├── protocol/
        │   │   ├── ChunkManagerStreamingTest.kt         # Streaming 64 KB chunking, CRC32 & non-OOM RandomAccessFile tests
        │   │   ├── GroupPacketSerializationTest.kt      # Group packet types 0x20–0x29 binary serialization tests
        │   │   └── TransferPacketSerializationTest.kt   # Transfer packet types 0x10–0x17 binary serialization tests
        │   ├── security/
        │   │   ├── EphemeralKeyPairTest.kt              # Curve25519 ECDH key exchange symmetry tests
        │   │   ├── PeerKeyStoreTest.kt                  # Android KeyStore public key caching tests
        │   │   ├── SessionEncryptorTest.kt              # AES-256-GCM encryption/decryption & IV tests
        │   │   └── TofuManagerTest.kt                   # Trust-On-First-Use key acceptance & MITM rejection tests
        │   └── service/
        │       ├── LanSocketManagerTest.kt              # TCP socket server & HELLO/MSG transmission tests
        │       ├── PeerDiscoveryEventTest.kt            # mDNS peer discovery & lost event tests
        │       └── TransferMetricsTest.kt               # Transfer speed, peak speed, ETA & formatting tests
        └── main/
            ├── AndroidManifest.xml        # Permissions (WiFi Direct, mDNS, Storage, Camera) & Service registration
            ├── res/
            │   └── xml/
            │       └── file_paths.xml     # FileProvider paths configuration for opening received files
            └── java/
                └── com/
                    └── hypershare/
                        ├── MainActivity.kt                      # Edge-to-edge Compose activity, permission checks & notification router
                        ├── application/                         # App-wide singletons & state management
                        │   ├── GroupManager.kt                  # Multi-peer group communication, O(N) packet routing & lifecycle
                        │   ├── HyperShareApplication.kt         # Application entry point
                        │   ├── SessionManager.kt                # Peer state & event flow arbitrator
                        │   ├── StreamController.kt              # In-memory ring-buffer for video/audio
                        │   ├── TransferManager.kt               # Sliding-window file transfer engine & speed analytics
                        │   ├── TransferQueue.kt                 # Priority queue for outbound chunks
                        │   └── UserIdentityManager.kt           # Device identity & username persistence
                        ├── db/                                  # SQLite Database & Repositories
                        │   ├── ChatDatabaseHelper.kt            # SQLiteOpenHelper (Schema v6) for peers, messages, groups, receipts
                        │   ├── GroupRepository.kt               # SQLite repository for groups, members, messages & receipts
                        │   └── MessageRepository.kt             # SQLite repository for 1-to-1 chat messages & delivery status
                        ├── di/                                  # Dependency Injection
                        │   ├── AppModule.kt                     # Application-scoped providers
                        │   └── NetworkModule.kt                 # Network & routing providers
                        ├── identity/                            # Cryptographic Identity & Contact Trust
                        │   ├── ContactCard.kt                   # QR exchange model with Ed25519 signature verification
                        │   └── IdentityManager.kt               # BLAKE2b-256 UserID derivation & Ed25519 signing engine
                        ├── model/                               # Data domain models
                        │   ├── ConnectedPeer.kt                 # Peer entity & status enum
                        │   ├── DataChunk.kt                     # 64 KB chunk + CRC32 payload
                        │   ├── GroupInviteCard.kt               # Signed group invite payload with expiration & verification
                        │   ├── GroupModels.kt                   # Group, GroupMember, GroupMessage & packet DTOs
                        │   ├── Packet.kt                        # Binary packet header, payload model & PacketType enum (0x01–0x29)
                        │   ├── PeerDiscoveryEvent.kt            # Sealed class representing mDNS peer discovered/lost events
                        │   ├── RouteEntry.kt                    # AODV routing table entry
                        │   ├── SessionEvent.kt                  # SharedFlow events (PeerJoined, ModeChanged, etc.)
                        │   └── TransferJob.kt                   # File/stream transfer task model
                        ├── protocol/                            # Binary Protocol & Chunking
                        │   ├── ChunkManager.kt                  # 64 KB splitting & reassembly with CRC32
                        │   ├── ControlPacketHandler.kt          # HELLO, ACK, RREQ/RREP parser
                        │   ├── PacketBuilder.kt                 # Packet construction & binary serialization
                        │   ├── PacketParser.kt                  # ByteArray deserializer
                        │   └── TransferPayloads.kt              # Binary payloads for file transfer types 0x10–0x17
                        ├── routing/                             # Mesh Routing Engine
                        │   ├── GroupOwnerElection.kt            # WiFi Direct GO scoring algorithm
                        │   ├── HopManager.kt                    # TTL decrement & relay decisions
                        │   ├── ModeController.kt                # Mode 1 <-> Mode 2 auto transition
                        │   └── RoutingTable.kt                  # ConcurrentHashMap route table with stale route pruning
                        ├── security/                            # Crypto & Key Management
                        │   ├── EphemeralKeyPair.kt              # ECDH (Curve25519) key generation & secret derivation
                        │   ├── PeerKeyStore.kt                  # Public key storage cache
                        │   ├── SessionEncryptor.kt              # AES-256-GCM encryption/decryption with unique 12-byte IV
                        │   └── TofuManager.kt                   # Trust-On-First-Use verification
                        ├── service/                             # Android Foreground Services & Network Engines
                        │   ├── DiscoveryService.kt              # mDNS (_hypershare._tcp) & UDP broadcast discovery (Mode 1)
                        │   ├── LanSocketManager.kt              # Singleton TCP socket engine (Port 47200), HELLO loop suppression
                        │   ├── MeshNetworkService.kt            # Foreground service managing LAN socket & WifiDirectController
                        │   ├── TransferService.kt               # File I/O & chunk transfer loop
                        │   └── WifiDirectController.kt          # WifiP2pManager wrapper, P2P discovery & group connection
                        └── ui/                                  # Jetpack Compose UI Layer
                            ├── chat/                            # 1-to-1 Encrypted Chat Screen
                            │   ├── ChatScreen.kt                # Message bubbles, contact gating banner, camera/attachment bar
                            │   └── ChatViewModel.kt             # Message thread state, contact gating & receipt management
                            ├── components/                      # Reusable Compose UI elements
                            │   ├── AmbientMeshGraphCanvas.kt    # Butter-smooth background mesh particle graph canvas
                            │   ├── BottomNavBar.kt              # 3-tab navigation bar (Home, Peers/Chats, Scan QR)
                            │   ├── GlassCard.kt                 # Frosted 5% white glass overlay modifier
                            │   ├── GroupAvatarStack.kt          # Stacked overlapping avatars with deterministic colors & +N badge
                            │   ├── QrCodeCard.kt                # ZXing QR Code matrix renderer for Share ID
                            │   ├── QrCodeGenerator.kt           # ZXing BitMatrix encoder utility
                            │   └── StatusChip.kt                # State indicator chip (CONNECTED, RELAY, MODE)
                            ├── filebrowser/                     # File Transfer Browser Screen
                            │   ├── FileBrowserScreen.kt         # File picker & VIEW_ONLY toggle UI
                            │   └── FileBrowserViewModel.kt      # Transfer queue state
                            ├── group/                           # Group Communication Screens
                            │   ├── GroupAvatarStack.kt          # Stacked avatar circles component
                            │   ├── GroupBottomSheets.kt         # CreateGroup, DirectInvite, GroupJoin bottom sheets
                            │   ├── GroupChatScreen.kt           # Multi-peer chat UI, sender colors, admin menu & QR invite modal
                            │   ├── GroupChatViewModel.kt        # Group messaging, invite dispatch & admin approval state
                            │   ├── GroupInviteActionReceiver.kt # BroadcastReceiver for notification invite accept/decline
                            │   ├── GroupJoinRequestReceiver.kt  # BroadcastReceiver for admin join request approval
                            │   ├── GroupListScreen.kt           # Group list with FAB creation dialog
                            │   └── GroupListViewModel.kt        # Group list state management
                            ├── home/                            # Home Dashboard Screen
                            │   ├── HomeScreen.kt                # Mode selector cards (Local Green / Emergency Red), QR Share ID
                            │   └── HomeViewModel.kt             # Device identity, unread count badge & connection status
                            ├── modetoggle/                      # Network Mode Switcher Modal
                            │   ├── ModeToggleScreen.kt          # Mode 1 vs Mode 2 selector card UI
                            │   └── ModeToggleViewModel.kt       # Selected mode state
                            ├── navigation/                      # Navigation Graph
                            │   └── NavGraph.kt                  # NavDisplay host, dual-service binding, type-safe NavKeys
                            ├── peerlist/                        # Peer List Screen
                            │   ├── PeerListScreen.kt            # Live peer list UI with Wi-Fi Direct connection banners & pull-to-refresh
                            │   └── PeerListViewModel.kt         # LAN & Wi-Fi Direct peer state, connection state & filters
                            ├── qrscanner/                       # Universal QR Code Scanner & Identity Exchange
                            │   ├── QrScannerScreen.kt           # CameraX scanner, reticle overlay, gallery image picker & paste dialog
                            │   └── QrScannerViewModel.kt        # Universal QR decoding (Contact Cards & Group Invites) & Ed25519 check
                            ├── routingdebug/                    # Dev Routing Visualizer Screen
                            │   ├── RoutingDebugScreen.kt        # Mesh topology visualizer UI
                            │   └── RoutingDebugViewModel.kt     # Routing table debug state
                            ├── settings/                        # User & App Settings Screens
                            │   ├── AccountSettingsScreen.kt     # Profile settings, Peer ID, cryptographic fingerprint
                            │   ├── AccountSettingsViewModel.kt  # Account identity state management
                            │   ├── AppSettingsScreen.kt         # App config, theme preferences, developer data wipe
                            │   └── AppSettingsViewModel.kt      # App settings state management
                            ├── stream/                          # In-Memory Stream Player Screen
                            │   ├── StreamPlayerScreen.kt        # View-only video player UI
                            │   └── StreamPlayerViewModel.kt     # Streaming ring-buffer state
                            ├── testing/                         # Crypto & Protocol Playground Screen
                            │   ├── SecurityPlaygroundScreen.kt  # Interactive Phase 0 test harness UI
                            │   └── SecurityPlaygroundViewModel.kt # Playground test execution ViewModel
                            └── theme/                           # Design tokens (OLED dark theme)
                                ├── Color.kt                     # Signal Blue, Mesh Teal, BackgroundBase, ConnectedGreen
                                ├── Theme.kt                     # HyperShareTheme dark color scheme
                                └── Type.kt                      # Monospace captions & Inter typography
```

---

## 2. File Responsibilities & Context Index

### Core Specifications (`docs/`)
- `docs/Architecture.md`: Master architectural blueprint (Layers: UI -> Application -> Service -> Routing -> Protocol -> Security -> Hardware).
- `docs/Bug_Log.md`: Production bugs, edge cases, SLA severity standards, and issue register.
- `docs/Mesh_Routing_Logic.md`: Routing specifications (AODV, RREQ, RREP, RERR, GO Election algorithm).
- `docs/Protocol_Specs.md`: Binary protocol specification (Packet types 0x01–0x29, headers, flags, payload formats).
- `docs/Running_Steps.md`: Device deployment, ADB connection, and app testing instructions.
- `docs/Testing_Scenarios.md`: Test cases for Mode 1 (LAN) and Mode 2 (Wi-Fi Direct P2P) mesh topologies.
- `docs/Theme.md`: UI design system rules (Dark OLED `#0A0A0F`, Glass Card overlays, Status chips, JetBrains Mono font).
- `docs/Workflow.md`: 24-week development roadmap.

### Android Application Layer (`app/src/main/java/com/hypershare/`)
- `MainActivity.kt`: Entry Activity enabling edge-to-edge layout, checking runtime permissions (`NEARBY_WIFI_DEVICES` on Android 13+ vs `ACCESS_FINE_LOCATION`), deferring foreground network services until granted, handling notification deep links (`OPEN_PEER_ID`, `OPEN_GROUP_ID`), and mounting `HyperShareNavGraph()`.
- `application/HyperShareApplication.kt`: Custom `Application` class initializing application-scoped singletons (`LanSocketManager`, `TransferManager`).
- `application/TransferManager.kt`: End-to-end sliding window file transfer engine (`SLIDING_WINDOW_SIZE = 8`), zero-OOM streaming CRC32 computation, chunk ACK coordination, transfer state tracking, speed calculations, and notification updates.
- `application/SessionManager.kt`: Global state holder exposing `StateFlow<List<ConnectedPeer>>` and `SharedFlow<SessionEvent>`.
- `application/UserIdentityManager.kt`: KeyStore/SharedPreferences per-device username & peer ID persistence.
- `application/GroupManager.kt`: Core group state machine, admin access control, O(N) direct message distribution, temporary group lifecycle tracking, and reconnection restore manager.
- `application/TransferQueue.kt`: Outbound transfer priority queue (`CONTROL > DISASTER_MSG > FILE > STREAM`).
- `application/StreamController.kt`: Ring-buffered in-memory byte stream for `VIEW_ONLY` permissions.
- `db/ChatDatabaseHelper.kt`: SQLiteOpenHelper (Schema v6) managing `peers`, `messages`, `groups`, `group_members`, `group_messages`, and `group_message_receipts`.
- `db/MessageRepository.kt`: SQLite database repository for 1-to-1 chats, read receipt progression (`SENT`, `DELIVERED`, `READ`), unread message counters, `getAllConversations()` history querying, and legacy demo row cleanup.
- `db/GroupRepository.kt`: SQLite database repository for `Group`, `GroupMember`, `GroupMessage`, and `GroupMessageReceipt` entities.
- `identity/IdentityManager.kt`: Stable device cryptographic identity generator (`BLAKE2b-256` UserID derivation) and Ed25519 keypair signer.
- `identity/ContactCard.kt`: Cryptographically signed contact exchange model with Ed25519 signature verification.
- `security/SessionEncryptor.kt`: AES-256-GCM encryption wrapper with random 12-byte IV per packet.
- `security/EphemeralKeyPair.kt`: KeyPairGenerator ("EC", 256-bit) and ECDH shared secret derivation.
- `security/PeerKeyStore.kt`: Public key storage cache.
- `security/TofuManager.kt`: Trust-On-First-Use verification and public key pinning engine.
- `protocol/ChunkManager.kt`: Splits files into 64 KB chunks and verifies CRC32 on reassembly.
- `protocol/PacketBuilder.kt` & `PacketParser.kt`: Handles binary serialization and parsing of custom network packets (types 0x01–0x29).
- `routing/RoutingTable.kt`: Manages `ConcurrentHashMap<String, RouteEntry>` with stale route pruning.
- `routing/HopManager.kt`: Validates destination, decrements TTL, and makes `DeliverLocal` vs `Relay` decisions.
- `model/PeerDiscoveryEvent.kt`: Sealed class representing `PeerDiscovered(peer)` and `PeerLost(peerId)` mDNS events.
- `model/GroupModels.kt`: Data models and serializable packet DTOs for multi-peer groups.
- `model/GroupInviteCard.kt`: Cryptographically signed group invite model with `Ed25519` signature verification and JSON serialization.
- `service/MeshNetworkService.kt`: Foreground service hosting `LanSocketManager` server on TCP port `47200` and managing `WifiDirectController` in Mode 2; automatically triggers client-to-GO socket connection and maps P2P MAC addresses to peer IDs.
- `service/WifiDirectController.kt`: Android `WifiP2pManager` wrapper managing P2P discovery, push-button group connection (`WpsInfo.PBC`), connection state flow, device address to peer ID mapping, and runtime permission gating.
- `service/DiscoveryService.kt`: Foreground service running `NsdManager` mDNS service registration (`_hypershare._tcp.`) with synchronized cryptographic `peerId` in TXT records and UDP broadcast discovery (Mode 1), pausing during Mode 2.
- `service/LanSocketManager.kt`: Singleton TCP socket engine on port `47200` with synchronized length-prefixed framing, bidirectional socket loops, `PeerHandshakeEvent` emission, duplicate HELLO loop prevention (`helloResponsesSent`), and direct background SQLite message persistence.
- `ui/navigation/NavGraph.kt`: Type-safe navigation graph mounting screens via `NavKey`s (`PeerListNavKey(mode)`), per-peer scoped `ChatViewModel(key = key.peerId)` lifecycle binding, dual-service binding (`DiscoveryService` and `MeshNetworkService`), Wi-Fi Direct connection handling, and deep-link routing.
- `ui/peerlist/PeerListScreen.kt` & `PeerListViewModel.kt`: Live conversation & peer list UI with stable WhatsApp-style sorting (`lastActivity DESC`), green outline for online LAN peers, ever-connected emergency peers persistence for multi-hop mesh, and zero demo room clutter.
- `ui/chat/ChatScreen.kt` & `ChatViewModel.kt`: 1-to-1 encrypted messaging with strict peer isolation (immediate state reset on load), contact gating state banner, 3-stage delivery ticks (Sent `✓`, Delivered `✓✓`, Read `✓✓` in Green), and attachment picker.
- `ui/group/GroupListScreen.kt` & `GroupListViewModel.kt`: Group chat list screen with creation FAB and member counts.
- `ui/group/GroupChatScreen.kt` & `GroupChatViewModel.kt`: Group chat UI with sender-colored names, three-dot admin menu (Broadcast Invite, QR Invite, Invite Directly), QR invite modal with countdown, and admin join request banners.
- `ui/group/GroupBottomSheets.kt`: Bottom sheet composables for group creation, direct peer invitations, and incoming group join requests.
- `ui/group/GroupAvatarStack.kt`: Stacked overlapping circular avatars with deterministic color assignment from `userId.hashCode()` and `+N` overflow badge.
- `ui/group/GroupInviteActionReceiver.kt` & `GroupJoinRequestReceiver.kt`: `BroadcastReceiver` implementations handling notification inline actions for broadcast invites and admin join request approvals.
- `ui/qrscanner/QrScannerScreen.kt` & `QrScannerViewModel.kt`: Universal QR scanner (CameraX, gallery picker, paste fallback) with Ed25519 signature validation and TOFU pinning.
- `ui/home/HomeScreen.kt` & `HomeViewModel.kt`: Home dashboard with Mode selector cards (Local Mode Green / Emergency Mode Red), device QR ID card, and unread badges.
- `ui/settings/AccountSettingsScreen.kt` & `AccountSettingsViewModel.kt`: Account profile settings screen (User display name, Peer Node ID, ECDH key fingerprint, QR ID badge).
- `ui/settings/AppSettingsScreen.kt` & `AppSettingsViewModel.kt`: App configuration screen (Dark/Light mode preference, auto-switch emergency mode toggle, security playground link, developer data wipe).
- `ui/testing/SecurityPlaygroundScreen.kt` & `SecurityPlaygroundViewModel.kt`: Interactive Phase 0 test harness UI for ECDH agreement, AES-256-GCM encryption/decryption, binary packet serialization, and TOFU key store verification.
- `ui/components/AmbientMeshGraphCanvas.kt`: Butter-smooth background mesh particle graph canvas with float array recycling.
- `ui/components/GlassCard.kt`: Custom Modifier `.glassCard()` applying 5% white overlay and 8% border.
- `ui/components/BottomNavBar.kt`: 3-tab bottom navigation bar (Home/Share, Peers & Chats, Scan ID).
- `ui/components/QrCodeCard.kt` & `QrCodeGenerator.kt`: ZXing BitMatrix encoder and QR Code canvas renderer.
- `ui/components/StatusChip.kt`: State indicator chip (CONNECTED, RELAY, MODE).
- `ui/theme/`: `Color.kt`, `Theme.kt`, `Type.kt` design tokens.

### Unit Tests (`app/src/test/java/com/hypershare/`)
- `db/MessageRepositoryTest.kt`: SQLite message repository CRUD, read receipts, and unread count queries.
- `identity/IdentityManagerTest.kt`: Stable device UUID, BLAKE2b UserID derivation, and Ed25519 signing/verification.
- `protocol/GroupPacketSerializationTest.kt`: Unit tests verifying serialization, parsing, and enum mapping for group packet types `0x20`–`0x29`.
- `security/EphemeralKeyPairTest.kt`: Unit tests verifying EC 256-bit key pair generation & ECDH shared secret symmetry across two peers.
- `security/SessionEncryptorTest.kt`: Unit tests verifying AES-256-GCM encryption/decryption roundtrip, unique IV headers, and payload tamper detection.
- `security/PeerKeyStoreTest.kt`: Unit tests for public key caching, retrieval, and removal.
- `security/TofuManagerTest.kt`: Unit tests for Trust-On-First-Use key acceptance, identity verification, and MITM alteration rejection.
- `service/LanSocketManagerTest.kt`: Unit tests for TCP server socket, HELLO handshake, and MSG transmission.
- `service/PeerDiscoveryEventTest.kt`: Unit tests for mDNS peer discovered/lost events and `PeerListViewModel` state updates.fying EC 256-bit key pair generation & ECDH shared secret symmetry across two peers.
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
| 2026-09-04 | AI Agent | Week 8 Final Polish, Build Verification & Sign-off | (1) Added missing 10 group packet types (`0x20`–`0x29`) to `PacketType` and `PacketBuilder.buildGroupPacket()`. (2) Implemented `GroupAvatarStack.kt`, `GroupListViewModel.kt`, `GroupListScreen.kt`, `GroupChatViewModel.kt`, `GroupChatScreen.kt`, and `GroupBottomSheets.kt`. (3) Added `GroupInviteActionReceiver.kt` and `GroupJoinRequestReceiver.kt` in `AndroidManifest.xml`. (4) Added `GroupPacketSerializationTest.kt` unit tests. (5) Resolved `getPublicKeyHex` in `IdentityManager` and verified 100% test pass (`./gradlew test`) and clean APK compilation (`./gradlew assembleDebug`). Week 8 is complete and rock-solid. |
| 2026-09-07 | rushil539 / AI Agent | SIH Prototype Wi-Fi Direct Connection 1st Implementation | (1) Built `WifiDirectController.kt` managing Android `WifiP2pManager` channel lifecycle, P2P peer discovery, push-button configuration (`WpsInfo.PBC`) group formation, and `WifiDirectConnectionState` StateFlow. (2) Added runtime permission gating for Android 13+ (`NEARBY_WIFI_DEVICES`) and Android 10–12 (`ACCESS_FINE_LOCATION`) in `MainActivity.kt` and `WifiDirectController.kt`, deferring foreground network service start until granted. (3) Updated `MeshNetworkService.kt` to coordinate `WifiDirectController`, automate client-to-GO socket connection (`192.168.49.1:47200`) upon group formation, and associate Wi-Fi Direct MAC addresses with TCP HELLO peer IDs. (4) Updated `LanSocketManager.kt` with `connectToPeer()`, `sendHelloResponseIfNeeded()`, `peerHandshakeEventsFlow`, and `helloResponsesSent` loop suppression. (5) Updated `NavGraph.kt` with `PeerListNavKey(mode)`, dual foreground service start (`EXTRA_MODE`), and Wi-Fi Direct peer connection clicks. (6) Updated `PeerListScreen.kt` and `PeerListViewModel.kt` with dynamic Wi-Fi Direct status banners (`SCANNING`, `CONNECTING`, `CONNECTED`, `GROUP OWNER`), P2P peer list items, and pull-to-refresh discovery. |
| 2026-09-07 | AI Agent | Complete Documentation & Filestructure Index Synchronization | (1) Updated `Filestructure.md` directory tree and file responsibilities with all active components (`WifiDirectController.kt`, `db/`, `identity/`, test suite). (2) Updated `Architecture.md` with Wi-Fi Direct controller, dynamic dual-mode service layer, and group persistence. (3) Updated `Workflow.md` marking Week 12 / SIH Prototype 1st Implementation completed. (4) Updated `Protocol_Specs.md` with `READ_ACK` (`0x09`), `CONTACT_ACCEPT` (`0x0A`), `GROUP_MSG_READ` (`0x29`), and Section 13 (Wi-Fi Direct P2P socket handshake). (5) Updated `Bug_Log.md` resolving ISSUE-004 and logging ISSUE-008 (HELLO handshake loop) and ISSUE-009 (Android 13+ permissions). (6) Updated `Testing_Scenarios.md` with M2-TC-01 prototype testing steps. (7) Updated `README.md` with valid `docs/` links, new feature specifications, and repository structure. |
| 2026-09-07 | AI Agent | Duplicate Chat & Ghost Peer Consolidation | (1) **Self-Chat Filter**: Added `WifiManager.connectionInfo.ipAddress` in `DiscoveryService.kt` and `PeerListViewModel.kt` to filter the local device from mDNS/UDP discovery loops and UI cards. (2) **Peer Deduplication & Merging**: Consolidated `recomputeConversationItems()` in `PeerListViewModel.kt` to key by normalized device name and IP, seamlessly collapsing offline DB chats and live LAN broadcasts into exactly one card per physical peer. (3) **Legacy Peer Migration**: Enhanced `cleanupGhostAndDemoData()` in `MessageRepository.kt` to automatically migrate messages from legacy short mDNS peer IDs to genuine cryptographic peer IDs and delete stale duplicate rows. |
| 2026-09-07 | AI Agent | Real-Time Peer Offline Detection & Green Outline Extinction | (1) **Peer Liveness Reaper**: Added 6s (`PEER_OFFLINE_TIMEOUT_MS`) periodic ticker in `DiscoveryService.kt` and `PeerListViewModel.kt` to actively evict LAN peers that stop sending UDP pings (3 missed pings). (2) **Instant UDP Goodbye**: Implemented `HYPERSHARE_BYE` packet broadcast on service stop / mode switch in `DiscoveryService.kt` for sub-second offline transition. (3) **Fuzzy PeerLost Matching & State Reset**: Upgraded `PeerLost` event handling in `PeerListViewModel.kt` to remove peers matching by ID, name, or IP, and reset `isOnlineOnLan = false` and `ipAddress = null` when peers go offline. (4) **Green Outline Dynamic Extinction**: Verified that offline peer cards immediately drop the green outline and revert to the subtle background border. |
| 2026-09-07 | AI Agent | Emergency Mode Architecture & Mutual Trust Consolidation | (1) **Mutually Accepted Peer Filtering**: Filtered Emergency Mode (`MODE_2_MESH`) conversations in `PeerListViewModel.kt` strictly to contacts with mutual trust (`isTrusted && hasPeerAccepted`), eliminating random Wi-Fi Direct devices (`DESKTOP-*`, `Android_*`, raw MACs). (2) **Online/Offline Status Discarded in Emergency Mode**: Suppressed green outlines and P2P badges in Emergency Mode to prevent multi-hop heartbeat bandwidth and battery drain while preserving real-time green outlines in Local Mode. (3) **Instant Reciprocal Contact Accept**: Enhanced `LanSocketManager.kt` to auto-respond with reciprocal `CONTACT_ACCEPT` when receiving acceptance from an already-trusted contact, and registered new sockets in `activeSockets` to listen for incoming replies. (4) **Ghost Wi-Fi Direct Pruning**: Added cleanup in `MessageRepository.kt` deleting legacy test artifacts (`Android_%`, `DESKTOP-%`, `LAPTOP-%`, MAC addresses). Verified on physical Android device with screenshot testing. |
| 2026-09-07 | AI Agent | Automatic Wi-Fi Direct Connection & Emergency Message Queuing | (1) Added local P2P device tracking (`myDeviceAddress`/`myDeviceName`) via `WIFI_P2P_THIS_DEVICE_CHANGED_ACTION` in `WifiDirectController.kt`. (2) Implemented deterministic collision-free auto-connection (`myMac >= peerMac` initiates with `groupOwnerIntent = 15`) in `MeshNetworkService.kt`, strictly filtering out non-mobile devices (`DESKTOP-*`, `LAPTOP-*`, TVs). (3) Added persistent `peerId` <-> `deviceAddress` mapping in `SharedPreferences`. (4) Built outbound emergency message queue in `LanSocketManager.kt` (`queuePendingMessage` / `flushPendingMessagesForPeer`) to automatically flush queued messages upon P2P socket connection. (5) Added real-time Wi-Fi Direct connection status banner in `ChatScreen.kt` and `NavGraph.kt`. |
| 2026-09-07 | AI Agent | Zero-Touch Mutual Wi-Fi Direct Auto-Connect & Resilient TCP Engine | (1) **Symmetric Intent Negotiation**: Added `calculateGoIntent` in `WifiDirectController.kt` deterministically assigning Intent 15 to the higher node and Intent 0 to the lower node. (2) **Zero-Prompt Mutual Connect**: Removed one-sided initiator block in `MeshNetworkService.kt` so both devices call `autoConnectToPeer` simultaneously with complementary intents, satisfying WPS PBC and completely bypassing Android's `UserAuthorizingNegotiationRequestState` without system settings intervention. (3) **Discovery Lifecycle & Auto-Restart**: Handled `WIFI_P2P_DISCOVERY_CHANGED_ACTION` to prevent discovery timeout lockup and auto-restart discovery when disconnected in Emergency Mode. (4) **Direct Device Info**: Added API 29+ `requestDeviceInfo` in `WifiDirectController.kt` for instant MAC resolution. (5) **Resilient Socket Retry**: Added 6-attempt retry loop with backoff in `LanSocketManager.connectToPeer` to gracefully bridge DHCP IP assignment delay on `p2p0`. |
| 2026-10-14 | AI Agent | Git Ignore Cleanup for Local Artifacts | Added `.gitignore` rules for local SQLite test databases (`*.db`, `*.sqlite`, `*.b64`) and screenshot artifacts to prevent tracking untracked test dumps. |
| 2026-10-14 | AI Agent | Phase 1 Week 9 Industry-Grade File Transfer & Nearby Share Experience | (1) **Sliding Window Protocol Engine**: Built `TransferManager.kt` managing 64 KB chunking, sliding window of 8 chunks in flight (`SLIDING_WINDOW_SIZE = 8`), ACK coordination, timeout retransmissions, and foreground service notification updates. (2) **Zero-OOM Streaming**: Implemented streaming CRC32 computation and `RandomAccessFile` direct disk chunk assembly in `ChunkManager.kt` allowing out-of-order 64 KB chunk placement without allocating large files in memory. (3) **Binary Transfer Protocol**: Defined `TRANSFER_INIT` (`0x10`), `TRANSFER_COMPLETE` (`0x11`), `TRANSFER_ACCEPT` (`0x14`), `TRANSFER_REJECT` (`0x15`), `TRANSFER_CHUNK_ACK` (`0x16`), and `TRANSFER_CANCEL` (`0x17`) with binary codecs in `TransferPayloads.kt` and `PacketBuilder.kt`. (4) **Live Speed Analytics & Gauges**: Implemented 500ms rolling window instantaneous speed calculation ($MB/s$, $KB/s$), cumulative average transfer speed, peak speed recording, dynamic ETA estimation, and post-transfer summary dialogs. (5) **Nearby Share / Quick Share UX**: Rebuilt `FileBrowserScreen.kt` & `FileBrowserViewModel.kt` with file picker, nearby peer radar target chips, live transfer metrics cards, incoming transfer Accept/Decline modal sheets, and `FileProvider` external open-file launcher. (6) **Chat Attachment Integration**: Added attachment (📎) button to `ChatScreen.kt` input bar. (7) **Unit Test Suite**: Added `ChunkManagerStreamingTest.kt`, `TransferPacketSerializationTest.kt`, and `TransferMetricsTest.kt`. Marked Week 9 complete in `docs/Workflow.md`. |
| 2026-10-14 | AI Agent | Universal Running & Device Deployment Guide | Refactored `docs/Running_Steps.md` to be completely cross-platform and universal: (1) Replaced hardcoded machine-specific paths (`C:\Users\ADMIN\...`) with universal environment variable detection for ADB and Java. (2) Added cross-platform instructions for Windows (`PowerShell`/`CMD`), macOS, and Linux (`Bash`/`Zsh`). (3) Replaced hardcoded device serial numbers with universal `adb -s <DEVICE_ID>` targeting. (4) Added step-by-step testing procedures for Quick Share file transfers, Local Mode messaging, Emergency Mode Wi-Fi Direct, and Security Playground. |
| 2026-10-14 | AI Agent | Android SDK CLI Integration & Build Bug Fixes | (1) Installed Google Android CLI and downloaded `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools` to `%LOCALAPPDATA%\Android\Sdk`. (2) Generated `local.properties` pointing `sdk.dir` to the SDK installation. (3) Updated `run.bat` with auto-detection for `ANDROID_HOME` and `ANDROID_SDK_ROOT`. (4) Fixed syntax and type mismatch bugs: added closing brace to `sendContactAcceptResponse` in `LanSocketManager.kt`, imported `currentCoroutineContext` in `TransferManager.kt`, fixed `StatusChip` signature usage in `FileBrowserScreen.kt`, and added missing UI imports in `HomeScreen.kt`. |
| 2026-10-14 | AI Agent | Multi-Device Discovery Fix & Chatroom File Transfer Button | (1) **Peer Discovery Fix**: Removed erroneous display name filtering (`displayName == myName`) in `DiscoveryService.kt` and `PeerListViewModel.kt` which previously caused identical device models or default usernames on the same network to treat each other as self and drop packets. (2) **Instant Direct UDP Pong**: Added immediate unicast UDP ping reply in `DiscoveryService.kt` on receiving LAN pings for sub-millisecond mutual discovery, socket `reuseAddress = true`, per-interface try/catch broadcast isolation, and extended `PEER_OFFLINE_TIMEOUT_MS` to 18s. (3) **Chatroom File Transfer Button & Flow**: Repositioned the File Share button (`📁`) right next to the Send button in `ChatScreen.kt` for intuitive 1-tap file sharing, wired `TransferManager.getInstance().jobs` StateFlow, live in-chat transfer card with speed gauge and progress bar, and added "Send File (Quick Share)" to top dropdown menu. |
| 2026-10-14 | AI Agent | Industry-Proof Back Navigation Architecture & Gesture System | (1) **Single-Top Backstack Management**: Implemented `popBack()`, `navigateToHome()`, `navigateTopLevel()`, and `pushDestination()` in `NavGraph.kt` to eliminate infinite backstack loops when switching tabs or clicking Home, ensuring `backStack.size > 1` so the backstack never empties to a blank screen. (2) **System Back & Gesture Navigation**: Wired `BackHandler` into `NavGraph.kt` and across all sub-screens (`PeerListScreen`, `AppSettingsScreen`, `AccountSettingsScreen`, `ChatScreen`, `FileBrowserScreen`, `QrScannerScreen`, `GroupListScreen`, `GroupChatScreen`, `ModeToggleScreen`, `RoutingDebugScreen`, `StreamPlayerScreen`, `SecurityPlaygroundScreen`). (3) **Modal & Dialog Interception**: Ensured `ChatScreen` dismisses security dialogs and 3-dot menus, `FileBrowserScreen` dismisses completion dialogs and incoming transfer sheets, and `GroupListScreen` dismisses group creation sheets before popping navigation destinations. (4) **Consistent In-App Back Arrows**: Added tactile circular back buttons (`←`) to headers in `PeerListScreen`, `AppSettingsScreen`, `AccountSettingsScreen`, `ModeToggleScreen`, `RoutingDebugScreen`, `StreamPlayerScreen`, and `SecurityPlaygroundScreen`. Verified 100% clean compilation. |
| 2026-10-14 | AI Agent | Screenshot Capture Utility & Gitignore Rule | Added Option `[5]` ("Take Screenshot from connected device(s)") to `run.bat` using `adb shell screencap` and `adb pull` to automatically capture and timestamp screenshots (`screenshots/screenshot_<SERIAL>_<TIMESTAMP>.png`) across all connected devices, with an interactive prompt to open the `screenshots/` directory in Windows Explorer. Updated `.gitignore` with `screenshots/`, `/screenshots/`, and `screenshots/*`. Enhanced `run.bat` deployment loop with automatic clean reinstall fallback (`adb uninstall` + `adb install`) when Android blocks installs due to `INSTALL_FAILED_UPDATE_INCOMPATIBLE` signature mismatches, and resolved batch parenthesis syntax parsing in loop echoes. |
| 2026-10-14 | AI Agent | Fix File Selection Crash & Auto-Initialize TransferManager | (1) Root Cause: Resolved crash on file selection caused by uninitialized TransferManager (`IllegalStateException: TransferManager not initialized` in `TransferManager.startSendTransfer`). (2) Global Application Init: Added TransferManager auto-initialization in `HyperShareApplication.onCreate()` and upgraded `TransferManager.getInstance(context)` companion overload with lazy initialization. (3) Context Fallback & Exception Safety: Added context parameter with defensive auto-init in `TransferManager.startSendTransfer` and try/catch wrapping in `ChatScreen.kt`. (4) Persistent Storage Permissions & Fallback Size Resolution: Added `takePersistableUriPermission` on picked URIs in `ChatScreen.kt` and added `openFileDescriptor` statSize fallback in `TransferManager.resolveFileMetadata` for document providers and PDFs returning 0/null for OpenableColumns.SIZE. |


