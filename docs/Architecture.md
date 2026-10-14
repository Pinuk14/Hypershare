# HyperShare — Android Architecture

> Infraless Disaster Communication System using Local Mesh Networks  
> B.Tech Final Year Project

---

## 1. High-Level Architecture Overview

HyperShare follows a **layered, modular architecture** built around Android's networking primitives. Each layer has a single responsibility; layers communicate only through defined interfaces (no god objects, no cross-cutting singletons except the session bus).

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Jetpack Compose)              │
│        Chat  │  File Browser  │  Peers  │  Settings          │
└──────────────────────────┬──────────────────────────────────┘
                           │ ViewModel (StateFlow)
┌──────────────────────────▼──────────────────────────────────┐
│                    Application Layer                          │
│   SessionManager  │  TransferQueue  │  StreamController      │
└──────────────────────────┬──────────────────────────────────┘
                           │ Coroutine channels / callbacks
┌──────────────────────────▼──────────────────────────────────┐
│                    Service Layer (Foreground Services)        │
│  MeshNetworkService  │  DiscoveryService  │  TransferService │
└────────────┬─────────────────────────┬────────────┬─────────┘
             │                         │            │
┌────────────▼──────────┐  ┌──────────▼──────┐  ┌─▼──────────────────┐
│  Routing Engine        │  │  Protocol Layer  │  │  Security Layer     │
│  (RoutingTable,        │  │  (PacketBuilder, │  │  (AES-GCM, ECDH,   │
│   HopManager,          │  │   PacketParser,  │  │   PeerKeyStore)     │
│   ModeController)      │  │   ChunkManager)  │  │                     │
└────────────┬──────────┘  └──────────┬──────┘  └─────────────────────┘
             │                         │
┌────────────▼─────────────────────────▼──────────────────────┐
│                  Transport / Hardware Layer                   │
│   WifiP2pManager  │  WifiManager  │  NSD  │  ServerSocket    │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Module Breakdown

### 2.1 UI Layer — Jetpack Compose + ViewModel

| Screen | Responsibility |
|---|---|
| `PeerListScreen` | Live list of discovered peers across Mode 1 (LAN) and Mode 2 (Wi-Fi Direct); shows connection state banner, mode toggle, unread badges, and group shortcut |
| `ChatScreen` | Per-peer messaging thread; mutual contact gating status banner, 3-stage delivery ticks (Sent, Delivered, Read), media attachment picker |
| `GroupListScreen` | Active permanent and temporary group list; creation dialogs, member badges, unread indicators |
| `GroupChatScreen` | Multi-peer group conversation; deterministic avatar stacks, member drawer, QR invite modal with countdown, admin join request approval banners |
| `FileBrowserScreen` | Browse and queue local files; permission toggle (view-only / downloadable) |
| `StreamPlayerScreen` | In-memory video/audio player fed by the StreamEngine |
| `ModeToggleScreen` | Explicit Mode 1 ↔ Mode 2 switch with visual indicator (green = WiFi, red = mesh) |
| `RoutingDebugScreen` | (Dev/debug build only) Live routing table visualizer, hop counts, TTL |

**ViewModel per screen** — each VM exposes `StateFlow<UiState>`. No screen talks directly to any service or manager; all communication goes through the VM's use-case calls.

**Navigation** — Type-safe navigation with `HyperShareNavGraph` handling `NavKey` destinations (`HomeNavKey`, `PeerListNavKey(mode)`, `ChatNavKey`, `GroupListNavKey`, `GroupChatNavKey`, `AccountSettingsNavKey`, `AppSettingsNavKey`, `QrScannerNavKey`). Deep-links from notification actions (`OPEN_PEER_ID`, `OPEN_GROUP_ID`) route directly to respective chatrooms.

---

### 2.2 Application Layer

#### `SessionManager` (singleton scoped to Application)
- Owns the list of `ConnectedPeer` objects.
- Arbitrates between Mode 1 and Mode 2 states.
- Broadcasts `SessionEvent`s (PeerJoined, PeerLost, ModeChanged) onto a `SharedFlow` that VMs collect.

#### `TransferQueue`
- Priority queue for outbound file chunks (`PRIORITY_CONTROL > PRIORITY_DISASTER_MSG > PRIORITY_FILE > PRIORITY_STREAM`).
- Handles pause/resume/cancel per transfer ID.
- Persists queue to `DataStore` so transfers survive process death.

#### `StreamController`
- Manages ring-buffer backed `StreamSession` objects.
- Produces `ByteArray` frames consumed by `StreamPlayerScreen` without writing to disk.

#### `UserIdentityManager` & `IdentityManager` (Trust & Identity Engine)
- Manages `stableDeviceUUID` v4 persisted in Android KeyStore (`hypershare_device_identity`).
- UserID derivation via `BLAKE2b-256(stableDeviceUUID + phone, salt=16-byte KeyStore salt)`.
- Ed25519 signing keypair generation and management for `ContactCard` and `GroupInviteCard` signatures.
- Enforces contact gating: unverified peers are restricted to 2 preview messages until mutual acceptance.

#### `GroupManager` (Group Communication Engine)
- Manages lifecycle of Permanent and Temporary groups (`Group`, `GroupMember`, `GroupMessage`).
- Coordinates direct O(N) message distribution across connected group members.
- Handles temporary group dissolve on disconnection and `GROUP_RESTORE` on reconnect.
- Manages admin member actions (`GROUP_MEMBER_ADD`, `GROUP_MEMBER_REMOVE`, `GROUP_KICKED`, `GROUP_DISSOLVE`).

---

### 2.3 Service Layer (Foreground Services & Controllers)

All network services run as **Android Foreground Services** with persistent system notifications to survive Doze Mode and background process management.

#### `MeshNetworkService`
- **Mode 1 (LAN):** Binds `LanSocketManager` server on TCP port `47200` to handle direct incoming connections on the local Wi-Fi subnet.
- **Mode 2 (Mesh / Wi-Fi Direct):** Hosts `WifiDirectController`. In group formation, Group Owner (GO) runs TCP server on port `47200` (`192.168.49.1`), while non-GO clients collect connection state and automatically invoke `LanSocketManager.connectToPeer()` to establish TCP connection with the GO.
- Collects `LanSocketManager.peerHandshakeEventsFlow` to map TCP HELLO source peer IDs to Wi-Fi Direct hardware addresses (`WifiP2pDevice.deviceAddress`).
- Responds to `EXTRA_MODE` ("MODE") intent: switches smoothly between Mode 1 and Mode 2, cleaning up active P2P groups and TCP connections upon mode switch.

#### `WifiDirectController`
- Wraps Android's `WifiP2pManager` with a reactive coroutine architecture.
- Dynamically gates permissions: checks `Manifest.permission.NEARBY_WIFI_DEVICES` on Android 13+ (API 33+) or `Manifest.permission.ACCESS_FINE_LOCATION` on older platforms before initiating P2P calls.
- BroadcastReceiver responds to:
  - `WIFI_P2P_STATE_CHANGED_ACTION`: Re-initiates peer discovery when Wi-Fi Direct is toggled on; resets state when disabled.
  - `WIFI_P2P_PEERS_CHANGED_ACTION`: Requests discovered peer list and updates `discoveredPeers` StateFlow.
  - `WIFI_P2P_CONNECTION_CHANGED_ACTION`: Requests `WifiP2pInfo` and publishes `WifiDirectConnectionState` (`isConnected`, `isGroupOwner`, `groupOwnerIpAddress`).
- Manages connection lifecycle: `connect(peer: WifiP2pDevice)` with `WpsInfo.PBC` push-button configuration, and cleans up via `cancelConnect`, `removeGroup`, `stopPeerDiscovery`.

#### `LanSocketManager` (Singleton Socket Engine)
- High-performance, non-blocking TCP socket engine supporting bidirectional framing over port `47200`.
- Implements synchronized `DataOutputStream` writes with length-prefixed binary packet framing.
- Handles binary packet lifecycle: `HELLO` (`0x01`), `MSG` (`0x03`), `READ_ACK` (`0x09`), `CONTACT_ACCEPT` (`0x0A`), and Group packets `0x20`–`0x29`.
- Emits `PeerHandshakeEvent` and responds to incoming HELLO packets while tracking `helloResponsesSent` to prevent infinite handshake loops.
- Provides `connectToPeer(targetIp, port, senderPeerId)` for outbound P2P connections to Group Owners and remote LAN peers.
- Dispatches incoming messages directly to SQLite persistence (`MessageRepository` and `GroupRepository`) and triggers high-priority heads-up notifications.

#### `DiscoveryService`
- **Mode 1 (LAN):** Dual mDNS (`_hypershare._tcp`) via Android `NsdManager` and UDP subnet broadcast on port `47201` with `WifiManager.MulticastLock` and loopback IP filtering (`isLocalDeviceAddress`).
- **Mode 2 (Disaster Mesh):** Stops mDNS advertisement and discovery when `EXTRA_MODE == MODE_2_MESH` to avoid interference and save battery.
- Emits `PeerDiscovered` and `PeerLost` events into `discoveryEventsFlow`.

#### `TransferService`
- Pulls from `TransferQueue` and writes chunks over the active socket to the destination peer.
- Handles chunk acknowledgement and retransmit logic.
- Reports progress back to `TransferQueue` which VMs observe.

---

### 2.4 Routing Engine

See `Mesh_Routing_Logic.md` for full detail. Summary:

| Component | Role |
|---|---|
| `RoutingTable` | In-memory `ConcurrentHashMap<PeerID, RouteEntry>` |
| `HopManager` | Decrements TTL, decides forward vs. drop |
| `GroupOwnerElection` | Scoring function for GO candidacy (battery, connectivity count, stability) |
| `ModeController` | Detects network loss and triggers Mode 1 → Mode 2 transition |

---

### 2.5 Protocol Layer

See `Protocol_Specs.md` for full packet specification. Summary:

| Component | Role |
|---|---|
| `PacketBuilder` | Constructs binary packets from typed Kotlin objects |
| `PacketParser` | Deserializes raw `ByteArray` into typed packet objects |
| `ChunkManager` | Splits files into chunks, reassembles, tracks CRC32 per chunk |
| `ControlPacketHandler` | Handles HELLO, ACK, ROUTE_UPDATE, ROUTE_ERROR types |

---

### 2.6 Security Layer

| Component | Implementation |
|---|---|
| Key exchange | ECDH (Curve25519) — one ephemeral keypair per session |
| Symmetric encryption | AES-256-GCM — unique IV per packet |
| Peer authentication | Self-signed X.509 cert pinned on first connect (TOFU model) |
| `PeerKeyStore` | Android `KeyStore`-backed storage of peer public keys |

All data on the socket is encrypted. There is no plaintext fallback. The binary protocol wraps encrypted payloads — the routing header fields (source, destination, TTL) are partially visible to relay nodes for routing purposes, but payload content is opaque.

---

## 3. Data Flow — Sending a File (Mode 2)

```
User selects file → FileBrowserScreen
        │
        ▼
TransferQueue.enqueue(fileUri, destinationPeerID, permission=VIEW_ONLY)
        │
        ▼
TransferService dequeues → reads file via ContentResolver
        │
        ▼ (chunks of 64 KB each)
ChunkManager.split() → List<DataChunk>
        │
        ▼
For each chunk:
  PacketBuilder.buildDataPacket(chunk, destinationPeerID, seqNum)
        │
        ▼
SecurityLayer.encrypt(packet)
        │
        ▼
RoutingEngine.resolveNextHop(destinationPeerID)
   ├── Direct peer? → write to peer's Socket directly
   └── Relay needed? → write to next-hop peer's Socket with TTL-1
        │
        ▼ (at destination)
PacketParser.parse() → DataChunk reassembled by ChunkManager
        │
        ▼
File written to Downloads/HyperShare/ (if DOWNLOADABLE)
or held in memory cache (if VIEW_ONLY)
```

---

## 4. Threading Model

| Thread/Coroutine Scope | Responsibility |
|---|---|
| `Main` dispatcher | UI state updates only |
| `IO` dispatcher | All socket reads, file I/O, `ContentResolver` calls |
| `Default` dispatcher | Crypto operations, packet parsing, routing decisions |
| `DiscoveryScope` | Long-lived coroutine in `DiscoveryService` |
| `TransferScope` | Long-lived coroutine per active transfer |

No blocking calls on the Main thread. All service→ViewModel communication via `StateFlow` or `SharedFlow`.

---

## 5. Key Dependencies

| Library | Purpose |
|---|---|
| Kotlin Coroutines + Flow | Async programming, event bus |
| Jetpack Compose + Navigation | UI |
| Jetpack DataStore (Proto) | Persistent settings and transfer queue |
| Hilt (Dagger) | Dependency injection |
| Bouncy Castle | ECDH, AES-GCM crypto primitives |
| Android `WifiP2pManager` | WiFi Direct (Mode 2) |
| Android `NsdManager` | mDNS discovery (Mode 1) |
| Timber | Logging (stripped in release build) |
| Kotlin Serialization | Configuration / peer metadata serialization |

**No third-party networking library** (no OkHttp, no Retrofit). All socket management is hand-rolled using `java.net.ServerSocket` / `java.net.Socket` to keep the binary protocol under full control.

---

## 6. Build Variants

| Variant | Difference |
|---|---|
| `debug` | Routing debug screen enabled, verbose logging, no ProGuard |
| `release` | All debug UI stripped, ProGuard/R8 enabled, logging disabled |
| `scenario-test` | Debug variant with simulated multi-hop stubs for emulator testing |

---

## 7. Permissions Required

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />   <!-- Required for WiFi Direct peer discovery on Android 10+ -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />    <!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />      <!-- Android 13+ -->
```

> **Note:** `ACCESS_FINE_LOCATION` is mandatory for WiFi Direct peer discovery on Android 10 and above — this is a platform requirement, not optional. Always explain this to users before requesting.
