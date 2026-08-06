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
                            │   └── StatusChip.kt                # State indicator chip (CONNECTED, RELAY)
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
                            └── routingdebug/                    # Dev Routing Visualizer Screen
                                ├── RoutingDebugScreen.kt        # Mesh topology visualizer UI
                                └── RoutingDebugViewModel.kt     # Routing table debug state
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
- `service/MeshNetworkService.kt`: Foreground service listening on TCP port `47200` (Mode 1) & managing WifiP2p (Mode 2).
- `service/DiscoveryService.kt`: Foreground service running `NsdManager` mDNS discovery (`_hypershare._tcp`).
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

### Unit Tests (`app/src/test/java/com/hypershare/security/`)
- `security/EphemeralKeyPairTest.kt`: Unit tests verifying EC 256-bit key pair generation & ECDH shared secret symmetry across two peers.
- `security/SessionEncryptorTest.kt`: Unit tests verifying AES-256-GCM encryption/decryption roundtrip, unique IV headers, and payload tamper detection.
- `security/PeerKeyStoreTest.kt`: Unit tests for public key caching, retrieval, and removal.
- `security/TofuManagerTest.kt`: Unit tests for Trust-On-First-Use key acceptance, identity verification, and MITM alteration rejection.

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
