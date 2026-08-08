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
- `ui/navigation/NavGraph.kt`: Type-safe navigation entries for all app screens including `AccountSettingsNavKey` and `AppSettingsNavKey`.
- `ui/testing/SecurityPlaygroundScreen.kt` & `SecurityPlaygroundViewModel.kt`: Interactive Phase 0 test harness UI for ECDH agreement, AES-256-GCM encryption/decryption, binary packet serialization, and TOFU key store verification.

### Unit Tests (`app/src/test/java/com/hypershare/`)
- `security/EphemeralKeyPairTest.kt`: Unit tests verifying EC 256-bit key pair generation & ECDH shared secret symmetry across two peers.
- `security/SessionEncryptorTest.kt`: Unit tests verifying AES-256-GCM encryption/decryption roundtrip, unique IV headers, and payload tamper detection.
- `security/PeerKeyStoreTest.kt`: Unit tests for public key caching, retrieval, and removal.
- `security/TofuManagerTest.kt`: Unit tests for Trust-On-First-Use key acceptance, identity verification, and MITM alteration rejection.
- `service/PeerDiscoveryEventTest.kt`: Unit tests for mDNS peer discovered/lost events and `PeerListViewModel` state updates.

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
| 2026-08-08 | AI Agent | Developer Mode App Data Wiping | Added a developer/testing option in AppSettingsScreen (hidden in release builds) to wipe all databases, preferences, and cache, restarting/killing the app to prepare it for fresh testing scenarios. |
| 2026-08-08 | AI Agent | Week 7 Workflow Task List Update | Updated Week 7 in `docs/Workflow.md` with refined tasks for device-stable identity generation (stableDeviceUUID v4 + BLAKE2b salt + Ed25519) and optional phone number onboarding metadata flow. |
| 2026-08-08 | AI Agent | Week 7 Device-Stable Cryptographic Identity Implementation | Added BouncyCastle dependency, built `IdentityManager.kt` (`stableDeviceUUID` v4 + KeyStore salt + `BLAKE2b-256` UserID + `Ed25519` keypair), built `ContactCard.kt` signature model, refactored `UserIdentityManager.kt`, and added `IdentityManagerTest.kt` unit tests. |
| 2026-08-08 | AI Agent | Chat Header & Peer Display Name Resolution | Added `peers` SQLite database table persistence (`savePeer` & `getPeerDisplayName`) in `MessageRepository.kt`, updated `ChatNavKey` & `NavGraph.kt` to pass `peerName`, updated `ChatViewModel.kt` to display user's saved device name in chat header instead of raw 64-char BLAKE2b UserID, and updated `LanSocketManager.kt` notifications. |
| 2026-08-08 | AI Agent | QR Code Contact Sharing & Camera/Gallery Scanner | (1) Added ZXing core & CameraX dependencies and `CAMERA` permission. (2) Created `QrCodeGenerator.kt` for real ZXing bitmatrix encoding and updated `QrCodeCard.kt` to render real QR matrices. (3) Created `QrScannerScreen.kt` & `QrScannerViewModel.kt` with CameraX preview, reticle overlay, gallery photo scanner (`decodeGalleryPhoto`), manual paste fallback, and Ed25519 signature verification. (4) Wired `QrScannerNavKey` into `NavGraph.kt` and `BottomNavBar.kt`. |
| 2026-08-08 | AI Agent | Contact-Gated Trust & Message Request Filtering | (1) Upgraded SQLite `ChatDatabaseHelper.kt` to version 2 with `is_trusted` and `public_key` columns. (2) Updated `MessageRepository.kt` (`markPeerAsTrusted`, `isPeerTrusted`, `savePeer`). (3) Updated `QrScannerViewModel.kt` to automatically set `is_trusted = 1` upon Ed25519 QR signature verification. (4) Added **Message Request Banner** (*"⚠️ Unknown Device — Unverified Peer"*) in `ChatScreen.kt` with **[Accept & Trust]** and **[Block]** buttons. (5) Added `🛡️ Verified Contact` vs `🌐 Discovered LAN` badges in `PeerListScreen.kt`. |
| 2026-08-08 | AI Agent | Un-paired Message Request Policy & Emoji Cleanup | (1) Enforced **2-message cap** and **300-character max length limit** for un-paired peers in `ChatViewModel.kt`. (2) Updated `ChatScreen.kt` with live character counter (`x/300 chars`), 2/2 msg lock state notice, and clean Message Request banner. (3) Added `getOutgoingMessageCountForPeer` in `MessageRepository.kt`. (4) Removed emojis across `ChatScreen`, `PeerListScreen`, and `QrScannerScreen` per user directive. |
| 2026-08-08 | AI Agent | Peer List Unread Message Badges & Chat Snippet Preview | (1) Added `getUnreadMessageCountForPeer` and `getLastMessageForPeer` queries to `MessageRepository.kt`. (2) Updated `PeerListScreen.kt` `PeerGlassListItem` to render WhatsApp-style Signal Blue circular pill badges for unread messages (`1`, `2`, `99+`), last message snippet preview, and formatted timestamps (`hh:mm a`). |
| 2026-08-08 | AI Agent | Global Unread Message Badges on Home & Header | (1) Added `getTotalUnreadMessageCount()` to `MessageRepository.kt`. (2) Added unread count circular pill badges on `HomeScreen.kt` Local Mode card. (3) Added unread badge chip on top header banner of `PeerListScreen.kt`. |
| 2026-08-08 | AI Agent | Symmetric Contact Accept Protocol & Notification Handshake | (1) Added `CONTACT_ACCEPT` (`0x0A`) packet type to `Packet.kt`. (2) Added `sendContactAcceptPacket` and `contactAcceptEventsFlow` in `LanSocketManager.kt`. (3) Updated `ChatViewModel.kt` to transmit `CONTACT_ACCEPT` when user accepts a contact and observe incoming accepts. (4) Updated `ChatScreen.kt` banner to notify user when peer has accepted their contact. |
| 2026-08-08 | AI Agent | Strict Mutual Contact Acceptance Enforcement | (1) Upgraded SQLite `ChatDatabaseHelper.kt` to `DATABASE_VERSION = 3` with `has_peer_accepted` column. (2) Added `hasPeerAcceptedUs` and `markPeerAcceptanceReceived` in `MessageRepository.kt`. (3) Enforced `isMutualTrustEstablished = isPeerTrusted && hasPeerAcceptedUs` in `ChatViewModel.kt` so single-sided acceptance CANNOT send unlimited messages until BOTH users accept. (4) Updated `ChatScreen.kt` header badge (*"Mutual Contact"* vs *"Pending Accept"*) and banner state machine (*"Awaiting Peer Acceptance"* vs *"Contact Accepted by Peer"*). |
| 2026-08-08 | AI Agent | Self-Discovery Bug Fix (Local IP Network Interface Filtering) | (1) Added `isLocalDeviceAddress(InetAddress)` in `DiscoveryService.kt` to iterate all local hardware network interfaces. (2) Filtered out local device IP addresses and `myPeerId` / `localServiceName` from both mDNS service resolver and UDP subnet ping receiver loops, preventing devices from discovering themselves after data wipes or network restarts. |
