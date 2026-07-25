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
│   ├── Mesh_Routing_Logic.md              # AODV-inspired routing logic & GO election scoring
│   ├── Protocol_Specs.md                  # Binary packet formats, headers, and control codes
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
- `docs/Mesh_Routing_Logic.md`: Routing specifications (AODV, RREQ, RREP, RERR, GO Election algorithm).
- `docs/Protocol_Specs.md`: Binary protocol specification (1 byte Version, 1 byte Type, 1 byte TTL, 4 byte SeqNum, etc.).
- `docs/Theme.md`: Design system rules (Dark OLED `#0A0A0F`, Glass Card overlays, Status chips, JetBrains Mono font).
- `docs/Workflow.md`: 24-week development roadmap.

### Android Application Layer (`app/src/main/java/com/hypershare/`)
- `MainActivity.kt`: Entry Activity enabling edge-to-edge layout and mounting `HyperShareNavGraph()`.
- `application/SessionManager.kt`: Global state holder exposing `StateFlow<List<ConnectedPeer>>` and `SharedFlow<SessionEvent>`.
- `application/TransferQueue.kt`: Outbound transfer priority queue (`CONTROL > DISASTER_MSG > FILE > STREAM`).
- `application/StreamController.kt`: Ring-buffered in-memory byte stream for `VIEW_ONLY` permissions.
- `security/SessionEncryptor.kt`: AES-256-GCM encryption wrapper with random 12-byte IV per packet.
- `security/EphemeralKeyPair.kt`: KeyPairGenerator ("EC", 256-bit) and ECDH shared secret derivation.
- `protocol/ChunkManager.kt`: Splits files into 64 KB chunks and verifies CRC32 on reassembly.
- `protocol/PacketBuilder.kt` & `PacketParser.kt`: Handles binary serialization and parsing of custom network packets.
- `routing/RoutingTable.kt`: Manages `ConcurrentHashMap<String, RouteEntry>` with stale route pruning.
- `routing/HopManager.kt`: Validates destination, decrements TTL, and makes `DeliverLocal` vs `Relay` decisions.
- `service/MeshNetworkService.kt`: Foreground service listening on TCP port `47200` (Mode 1) & managing WifiP2p (Mode 2).
- `service/DiscoveryService.kt`: Foreground service running `NsdManager` mDNS discovery (`_hypershare._tcp`).
- `ui/theme/Color.kt`: Color constants (`SignalBlue`, `MeshTeal`, `BackgroundBase`, `ConnectedGreen`, etc.).
- `ui/components/GlassCard.kt`: Custom Modifier `.glassCard()` applying 5% white overlay and 8% border.
- `ui/navigation/NavGraph.kt`: Type-safe navigation entries for all 6 app screens.

---

## 3. Change Log

| Timestamp | Developer / AI | Action | Description |
|---|---|---|---|
| 2026-07-25 | AI Agent | Initial Project Architecture Setup | Created initial Android Gradle project, package structure (`com.hypershare.*`), and stubbed all 9 architecture layers. |
| 2026-07-25 | AI Agent | Documentation Re-organization | Moved specification `.md` files (`Architecture.md`, `Mesh_Routing_Logic.md`, `Protocol_Specs.md`, `Testing_Scenarios.md`, `Theme.md`, `Workflow.md`) into `docs/` folder in root. Created `Filestructure.md`. |
