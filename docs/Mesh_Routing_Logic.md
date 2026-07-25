# HyperShare — Mesh Routing Logic

> Covers: Peer Discovery, Group Owner Election, Routing Table Schema, Multi-Hop Routing (AODV-inspired), Route Maintenance, and Mode Transition.

---

## 1. Overview

HyperShare's Mode 2 builds a **star-extended mesh** using Android WiFi Direct. Pure WiFi Direct creates a star topology (one Group Owner, all others as clients). HyperShare extends this into a multi-hop mesh by implementing an **application-layer routing protocol** on top of the WiFi Direct transport. Devices that are out of direct radio range can still receive messages by relaying them through intermediate nodes.

```
Physical WiFi Direct topology (star):
         [GO: Device A]
        /       |       \
   [Dev B]  [Dev C]  [Dev D]

Application-layer logical topology (mesh):
   [Dev B] ——— [Dev A] ——— [Dev C]
                  |
              [Dev D]
                  |
              [Dev E]   ← Device E connected to Dev D's hotspot (extended hop)
```

---

## 2. Peer Discovery

### Mode 1 (WiFi LAN — mDNS)

1. On `DiscoveryService` start, register a **NSD service** with:
   - Service name: `HyperShare-<DeviceID>` (DeviceID = first 8 chars of UUID, generated on first launch and persisted)
   - Service type: `_hypershare._tcp`
   - Port: `47200`
   - Attributes: `{"version": "1", "mode": "1", "name": "<DisplayName>"}`

2. Simultaneously run `NsdManager.discoverServices("_hypershare._tcp")`.

3. On discovery callback `onServiceFound`:
   - Resolve the service to get IP + port.
   - Emit `PeerDiscoveredEvent(peerId, ip, port, displayName)` into the session bus.
   - `SessionManager` adds to `PendingPeers` list; user initiates connection or auto-connects (configurable).

4. On `onServiceLost`: emit `PeerLostEvent`. Mark peer as `DISCONNECTED` in `RoutingTable`.

**Known issue:** `NsdManager` on Android has a bug where it stops discovering after ~30 minutes on some OEMs. Mitigation: restart discovery every 20 minutes with a scheduled coroutine.

---

### Mode 2 (WiFi Direct)

1. Call `WifiP2pManager.discoverPeers(channel, actionListener)`.
2. Register a `BroadcastReceiver` for `WIFI_P2P_PEERS_CHANGED_ACTION`.
3. On receipt: call `WifiP2pManager.requestPeers(channel, peerListListener)`.
4. For each `WifiP2pDevice` in the list:
   - Parse device name: HyperShare devices advertise `HS-<DeviceID>-<DisplayName>` as the WifiP2p device name.
   - Non-HyperShare devices are ignored (device name prefix filter).
   - Emit `P2PPeerDiscoveredEvent(wifiP2pDevice)`.

5. Connection is initiated by either device calling `WifiP2pManager.connect()` with a `WifiP2pConfig`.

**Re-discovery loop:** Run `discoverPeers()` every 15 seconds. WiFi Direct discovery stops itself after ~60 seconds; the loop keeps it alive.

---

## 3. Group Owner (GO) Election

### 3.1 Why GO Matters

In WiFi Direct, the Group Owner acts as the soft access point. All clients connect to the GO. The GO's IP is the routing hub for all direct-range communication. Poor GO selection (e.g., a device with 5% battery becomes GO) destabilizes the entire group.

### 3.2 GO Score Calculation

Before any device calls `WifiP2pManager.connect()`, it broadcasts a **HELLO packet** (see Protocol Specs) containing a GO candidacy score. The device with the highest score becomes GO.

```
GO_SCORE = (BATTERY_SCORE × 0.4) + (CONNECTIVITY_SCORE × 0.3) + (STABILITY_SCORE × 0.2) + (ROLE_PREFERENCE × 0.1)
```

| Component | Calculation | Max |
|---|---|---|
| `BATTERY_SCORE` | `batteryPercent` (0–100), clamp to 0 if charging=false and <15% | 100 |
| `CONNECTIVITY_SCORE` | `min(knownPeerCount × 20, 100)` — more known peers = better hub | 100 |
| `STABILITY_SCORE` | `min(uptimeSeconds / 60, 100)` — device uptime as stability proxy | 100 |
| `ROLE_PREFERENCE` | 100 if device is a tablet or has external power; 50 if phone; 0 if explicitly opted out | 100 |

The device with the **highest GO_SCORE** sets `WifiP2pConfig.groupOwnerIntent = 15` (max intent to be GO). All others set intent proportional to their score (0–14).

### 3.3 GO Election Flow

```
Step 1: Discovery finds other HyperShare devices.
Step 2: All devices broadcast HELLO (with GO_SCORE) via UDP broadcast on 192.168.49.255:47201 (pre-connection).
Step 3: Each device waits 3 seconds, collects all HELLO packets.
Step 4: Each device calculates: am I the highest scorer?
   └─ Yes: set groupOwnerIntent = 15, initiate connect()
   └─ No:  set groupOwnerIntent proportional to score, wait for invite or connect to highest scorer.
Step 5: WifiP2pManager finalizes GO role (Android negotiates the actual assignment; high intent wins ~95% of the time).
Step 6: GO calls WifiP2pManager.createGroup() if no group exists.
Step 7: Clients connect to GO via connect() with GO's device address.
```

### 3.4 GO Failure Recovery

If the GO device drops (battery dies, moves out of range):
1. All clients detect connection loss within `HELLO_TIMEOUT = 15 seconds`.
2. Remaining devices re-enter the GO election flow from Step 1.
3. New GO forms a new group; clients reconnect to new GO.
4. Routing table is rebuilt via RREQ/RREP flood.

**This is the most failure-prone step in the entire system.** During testing, allow 30–60 seconds for GO failover. This is a known WiFi Direct limitation.

---

## 4. Routing Table

### 4.1 Schema

```kotlin
data class RouteEntry(
    val destinationId: String,       // Target peer's UUID
    val nextHopId: String,           // Immediate neighbor to forward to
    val nextHopAddress: InetAddress, // IP of nextHopId
    val hopCount: Int,               // Number of hops to destination (1 = direct)
    val sequenceNumber: Int,         // AODV sequence number for freshness
    val lastUpdated: Long,           // System.currentTimeMillis() at last update
    val isValid: Boolean             // False = route is stale/broken
)

// Stored as:
val routingTable: ConcurrentHashMap<String, RouteEntry>  // key = destinationId
```

### 4.2 Direct Peer Entries

When a peer connects directly (hop count = 1), an entry is added immediately:

```
destinationId   = connectedPeer.id
nextHopId       = connectedPeer.id    (same as destination for direct peers)
nextHopAddress  = connectedPeer.socket.inetAddress
hopCount        = 1
sequenceNumber  = 0
lastUpdated     = now
isValid         = true
```

### 4.3 Route Expiry

Entries are pruned if:
- `lastUpdated` is older than `ROUTE_EXPIRY_MS = 30_000` (30 seconds), OR
- `isValid == false` (explicitly invalidated by RERR packet)

A background coroutine runs every 10 seconds to purge expired entries and emit `RoutingTableUpdatedEvent` to the debug screen.

---

## 5. Multi-Hop Routing Protocol (AODV-Inspired)

HyperShare uses a simplified version of AODV (Ad-hoc On-demand Distance Vector) because:
- AODV only discovers routes when needed (on-demand), minimizing overhead on a battery-constrained mesh.
- Routes are established reactively, which fits the unpredictable topology of a disaster environment.

### 5.1 Route Discovery — RREQ (Route Request)

When Node A wants to send to Node D but has no valid route:

```
1. A generates RREQ:
   { type: RREQ, srcId: A, destId: D, rreqId: <unique>, hopCount: 0, srcSeqNum: A.seqNum, destSeqNum: last known for D or 0 }

2. A broadcasts RREQ to all direct neighbors (B, C).

3. Each receiving node checks:
   a. Have I already seen this rreqId? → Drop (dedup cache, TTL 60s).
   b. Am I the destination (D)? → Send RREP back to A.
   c. Do I have a valid route to D with sequenceNumber ≥ destSeqNum? → Send RREP.
   d. Otherwise: increment hopCount, add A to my reverse route table, rebroadcast RREQ.

4. TTL cap: if hopCount > MAX_HOP_COUNT (8), drop RREQ.
```

### 5.2 Route Reply — RREP (Route Reply)

When Node D (or an intermediate node with a valid route) responds:

```
1. D sends RREP unicast back toward A:
   { type: RREP, srcId: D, destId: A, hopCount: 0, seqNum: D.seqNum, lifetime: 30000 }

2. Each intermediate node that forwards RREP:
   a. Increments hopCount.
   b. Adds a RouteEntry: destination=D, nextHop=previous RREP sender, hopCount=current.
   c. Forwards RREP toward A via reverse-path lookup.

3. Node A receives RREP → adds RouteEntry for D → starts sending data.
```

### 5.3 Route Error — RERR (Route Error)

When a next-hop node is unreachable:

```
1. Node B tries to forward a packet to Node C (which was its next hop to D).
2. Socket write fails (IOException or timeout).
3. Node B sends RERR:
   { type: RERR, affectedDests: [D, E, F] }  // all routes through C
4. All nodes receiving RERR:
   a. Mark routes through C as isValid=false.
   b. If they have active sessions to affected destinations, trigger RREQ.
```

### 5.4 Periodic HELLO Beacons

Every `HELLO_INTERVAL = 5000 ms`, each node sends a HELLO packet to all direct neighbors:
```
{ type: HELLO, srcId: self, batteryPct: current, hopCount: 0 }
```

If a direct neighbor misses `HELLO_MISS_THRESHOLD = 3` consecutive HELLOs (15 seconds), it is considered lost:
- Remove from direct peer list.
- Invalidate all routes through it.
- Trigger RERR for affected destinations.

---

## 6. Packet Forwarding Decision Tree

```
Receive packet destined for PeerID X:
        │
        ▼
Is X == self?
   YES → Deliver to local handler (message, file chunk, stream frame)
   NO  ↓
        │
        ▼
Lookup X in RoutingTable:
   NOT FOUND or INVALID → Initiate RREQ for X; buffer packet (max 5 packets, 10s timeout)
   FOUND ↓
        │
        ▼
RouteEntry.hopCount == 1?
   YES → Write directly to X's socket (direct peer)
   NO  ↓
        │
        ▼
Decrement packet TTL:
   TTL == 0 → Drop packet, send RERR if this was a relay
   TTL > 0  ↓
        │
        ▼
Write packet to nextHop.socket
   SUCCESS → Done
   FAILURE → Mark route invalid, send RERR, trigger RREQ retry
```

---

## 7. Sequence Number Management

Each device maintains a monotonically increasing sequence number (`deviceSeqNum`) persisted in `DataStore`. It is incremented:
- On every new RREQ generated by this device.
- On every RREP generated by this device as the destination.

Sequence numbers prevent routing loops and stale route acceptance. A route with a higher sequence number is always preferred over one with a lower sequence number (freshness over hop count).

---

## 8. Extended Topology: Chained WiFi Direct Groups

For scenarios with more devices than a single WiFi Direct group supports (typically 4–8 clients per GO), HyperShare supports **chained groups**:

```
[Group 1: GO=A]              [Group 2: GO=E]
   A — B — C — D     ←→     E — F — G — H
              └─── Bridge Device D acts as both client in Group 1
                             and GO in Group 2 (via mobile hotspot)
```

- Device D is in both groups simultaneously: as a WiFi Direct client to A, and as a mobile hotspot GO to E,F,G,H.
- D acts as a **bridge node**: it has routes to both groups and forwards packets between them.
- This is implemented by running two simultaneous `MeshNetworkService` sockets on D — one on the WiFi Direct interface, one on the hotspot interface.
- This is experimental — mark clearly in the paper and test thoroughly.

---

## 9. Routing Table State Diagram

```
     [RREQ Initiated]
            │
            ▼
     [Pending Route]  ←── RREQ timeout → [No Route / Error State]
            │
         RREP received
            │
            ▼
      [Active Route]  ──── RERR received ──→ [Invalid Route]
            │                                        │
            │ HELLO beacon missed × 3               RREQ retry
            ▼                                        │
      [Stale Route]  ────── 30s expiry ──────→ [Pruned]
```
