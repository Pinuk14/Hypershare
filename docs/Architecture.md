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
| `PeerListScreen` | Live list of discovered peers; shows signal-strength estimate, peer mode, transfer queue |
| `ChatScreen` | Per-peer messaging thread; displays delivery status (sent / relayed / received) |
| `FileBrowserScreen` | Browse and queue local files; permission toggle (view-only / downloadable) |
| `StreamPlayerScreen` | In-memory video/audio player fed by the StreamEngine |
| `ModeToggleScreen` | Explicit Mode 1 ↔ Mode 2 switch with visual indicator (green = WiFi, red = mesh) |
| `RoutingDebugScreen` | (Dev/debug build only) Live routing table visualizer, hop counts, TTL |

**ViewModel per screen** — each VM exposes `StateFlow<UiState>`. No screen talks directly to any service or manager; all communication goes through the VM's use-case calls.

**Navigation** — Compose Navigation with a single `NavHost`. Deep-links from notification actions (e.g., incoming transfer) route here.

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

---

### 2.3 Service Layer (Foreground Services)

All three run as **Android Foreground Services** with a persistent notification. This keeps them alive under Doze Mode and background process kills.

#### `MeshNetworkService`
- **Mode 1:** Binds to a `ServerSocket` on port `47200` and listens for TCP connections from peers on the same LAN.
- **Mode 2:** Owns the `WifiP2pManager` lifecycle — forms the group, maintains the GO (Group Owner) role or client role, and manages direct peer connections.
- Delegates all received packets to `RoutingEngine` for forwarding decisions.

#### `DiscoveryService`
- **Mode 1:** Uses Android's `NsdManager` (Network Service Discovery / mDNS) to advertise and discover `_hypershare._tcp` services on the local network.
- **Mode 2:** Uses `WifiP2pManager.discoverPeers()` and `WifiP2pManager.requestPeers()` with a BroadcastReceiver for peer list updates.
- On discovery, emits `PeerDiscoveredEvent` into the session bus.

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
