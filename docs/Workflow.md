# HyperShare — Week-by-Week Development Workflow

> B.Tech Final Year Project — 2 Semesters (~32 weeks)  
> Team size assumption: 2–4 members. Tasks marked [SOLO] can be solo; [PAIR] needs two people.

---

## Phase 0 — Foundation & Research (Weeks 1–3)

### Week 1 — Project Setup & Environment
**Goal:** Everyone has a working build environment; project skeleton compiles.

- [x] Create GitHub repository with branch protection (`main`, `dev`, feature branches)
- [x] Set up Android project with Gradle, Kotlin, Jetpack Compose, Hilt [PAIR]
- [x] Define package structure matching the Architecture layers
- [x] Set up CI (GitHub Actions) — lint + unit test on every PR
- [x] Write `README.md` skeleton (project description, setup instructions)
- [x] Literature review: WiFi Direct Android API quirks, WifiP2pManager known issues [SOLO per person]

**Deliverable:** Empty app that launches, with all modules stubbed as empty Kotlin files.

---

### Week 2 — Protocol & Architecture Design Lock
**Goal:** Final decisions on packet format and architecture are locked before any networking code is written.

- [x] Finalize `Protocol_Specs.md` — all packet types, header fields, chunk sizes [PAIR]
- [x] Finalize `Mesh_Routing_Logic.md` — routing table schema, GO election scoring [PAIR]
- [x] Create a shared `model/` package: `ConnectedPeer`, `RouteEntry`, `DataChunk`, `TransferJob` data classes
- [x] Review `Architecture.md` as a team; mark any disagreements and resolve them
- [x] Set up a physical device lab: label 3–4 Android devices (Device A, B, C, D), document their Android versions

**Deliverable:** All `.md` design docs finalized and committed. Data models committed.

---

### Week 3 — Security Layer Skeleton
**Goal:** Crypto primitives working in isolation before they touch networking.

- [x] Implement `EphemeralKeyPair` generation using Bouncy Castle (Curve25519) [SOLO]
- [x] Implement `SessionEncryptor` — AES-256-GCM encrypt/decrypt with unique IVs [SOLO]
- [x] Implement `PeerKeyStore` — Android KeyStore-backed public key persistence [SOLO]
- [x] Write unit tests for each crypto component (known plaintext → ciphertext → verify decryption) [PAIR]
- [x] Implement TOFU (Trust On First Use) key acceptance flow

**Deliverable:** Security layer passes all unit tests. No networking yet.

---

## Phase 1 — Mode 1: WiFi Local Network (Weeks 4–9)

### Week 4 — mDNS Discovery (Mode 1)
**Goal:** Devices on the same LAN can find each other automatically.

- [x] Implement `DiscoveryService` — register `_hypershare._tcp` service via `NsdManager` [PAIR]
- [x] Implement peer discovery listener — on discovery, emit `PeerDiscoveredEvent`
- [x] Build `PeerListScreen` integration — shows a flat list of discovered peers (name, IP)
- [x] Unit test peer discovery event handling & ViewModel state updates

**Deliverable:** Device A's peer list shows Device B, and vice versa, within 5 seconds.

---

### Week 5 — TCP Connection & HELLO Handshake
**Goal:** Two discovered peers can establish an encrypted TCP connection.

- [x] Implement `MeshNetworkService` Mode 1 — `ServerSocket` on port 47200 via `LanSocketManager`
- [x] Implement connection initiation from `PeerListScreen` (user taps a peer)
- [x] Implement HELLO packet exchange (`Protocol_Specs.md` packet type `0x01`)
- [x] Integrate Security Layer into the handshake — ECDH key exchange on connect
- [x] Update `PeerListScreen` to show "Connected" state after handshake

**Deliverable:** Device A and Device B complete an encrypted HELLO handshake. Logs show derived shared secret matches on both sides.

---

### Week 6 — Real-Time Messaging
**Goal:** Text messages flow bidirectionally over the encrypted channel.

- [x] Implement `PacketBuilder.buildMessagePacket()` and `PacketParser` for MSG type (`0x02`)
- [x] Implement `ChatScreen` UI — message input, message list, timestamps, delivery ticks
- [x] Wire ChatScreen → LanSocketManager → MeshNetworkService → Socket write
- [x] Incoming packet dispatch: socket read loop → PacketParser → MessageRepository → ChatScreen StateFlow
- [x] Test: Device A sends text over LAN socket, Device B receives it over local WiFi

**Deliverable:** Two-device chat works on Mode 1.

---

### Week 7 — Contact-Gated Communication (Mutual Peer Trust)
**Goal:** Only trusted mutual contacts can communicate. Identity is device-stable, phone number is optional metadata. No SIM required.

#### Identity Generation
- [ ] On first launch, generate a cryptographically random `stableDeviceUUID` (UUID v4) and store it in Android KeyStore under alias `hypershare_device_identity`. Never regenerate unless user explicitly resets identity.
- [ ] Generate Ed25519 keypair on first launch, store private key in KeyStore. Public key is distributed on every `ContactCard`.
- [ ] Implement `UserID = BLAKE2b(stableDeviceUUID.bytes, salt=16-byte KeyStore salt, len=32)`. The salt is also generated once and stored in KeyStore — it makes the `UserID` non-reversible even if the UUID leaks.
- [ ] Implement `IdentityManager` singleton: exposes `getUserId()`, `getPublicKey()`, `signData(ByteArray)`. All identity operations go through here.

**Deliverable:** Device-stable identity and optional phone metadata flow established. Contact exchange and mutual peer trust foundation ready for Week 8.

---

### Week 8 — Group Communication (Permanent & Temporary)
**Goal:** Permanent and temporary multi-peer group messaging with admin-based access control.

- [ ] Create Room `Group` and `GroupMessage` entities & `GroupRepository`
- [ ] Implement Group Creation flow (Admin generates UUID `groupId`, constructs group, sends `GROUP_CREATE`)
- [ ] Implement `GROUP_JOIN_ACK` handling and member list broadcast
- [ ] Implement Admin Member Management (`GROUP_MEMBER_ADD`, `GROUP_MEMBER_REMOVE`, `GROUP_KICKED`)
- [ ] Implement Direct Group Message Distribution (O(n) direct sends to `memberIds` with per-recipient `GROUP_MSG_ACK`)
- [ ] Implement Temporary Group Lifecycle (In-memory connected member counter → `isActive=false` on dissolve)
- [ ] Implement `GROUP_RESTORE` trigger when temporary group members reconnect on same network

**Deliverable:** Admin can create, add/remove members, and chat in permanent and temporary groups.

---

### Week 9 — File Transfer (Mode 1)
**Goal:** Files up to 500 MB transfer reliably with chunk acknowledgement.

- [ ] Implement `ChunkManager.split()` — 64 KB chunks, CRC32 per chunk
- [ ] Implement `PacketBuilder.buildDataPacket()` for DATA type
- [ ] Implement `TransferService` — dequeue → read file → send chunks in sequence
- [ ] Implement ACK packet handling — sender waits for ACK per chunk (sliding window: 8 chunks in flight)
- [ ] Implement receiver reassembly — `ChunkManager.reassemble()`
- [ ] Build `FileBrowserScreen` — file picker, permission toggle, transfer progress bar
- [ ] Test: 100 MB file transfer, verify CRC32 integrity of received file

**Deliverable:** File transfer works in Mode 1 with integrity verification.

---

### Week 10 — Media Streaming (Mode 1)
**Goal:** Videos stream in-memory without full download.

- [ ] Implement `StreamController` — ring buffer, frame production
- [ ] Implement STREAM packet type — streaming chunks with sequence numbers
- [ ] Implement `StreamPlayerScreen` — `VideoView` backed by `StreamController`'s output
- [ ] Handle buffer underrun: pause playback, display "Buffering…" indicator
- [ ] Permission enforcement: VIEW_ONLY streams are never written to disk

**Deliverable:** Device A streams a 30-second video to Device B. No file is written on Device B.

---

### Week 11 — Mode 1 Polish & Integration Testing
**Goal:** Mode 1 is feature-complete and stable under load.

- [ ] Run simultaneous messaging + file transfer + stream on the same connection — verify no deadlock
- [ ] Test with 4 devices on the same LAN: each connected to each other (full mesh on router)
- [ ] Transfer queue priority: control messages preempt file chunks
- [ ] Implement transfer resume after disconnect (DataStore-persisted queue)
- [ ] Fix all P1 bugs from testing
- [ ] Mode 1 documentation update

**Deliverable:** Mode 1 passes all `Testing_Scenarios.md` Mode 1 test cases.

---

## Phase 2 — Mode 2: Disaster Mesh Network (Weeks 12–20)

### Week 12 — WiFi Direct Fundamentals
**Goal:** Two devices form a WiFi Direct group without crashing.

- [ ] Implement `WifiP2pManager` lifecycle in `MeshNetworkService` — initialize, register receiver
- [ ] Implement `DiscoveryService` Mode 2 — `discoverPeers()`, `requestPeers()`
- [ ] Implement Group Owner election hook — use GO scoring function from `Mesh_Routing_Logic.md`
- [ ] Handle all `WifiP2pManager` error codes gracefully (BUSY, NO_SERVICE_REQUESTS, etc.)
- [ ] Manual test: Device A and Device B form a WifiP2p group; verify GO assignment in logs

**Deliverable:** Two-device WiFi Direct group is stable for 10 minutes.

---

### Week 13 — Multi-Client Group Topology
**Goal:** Three or more devices join the same WiFi Direct group.

- [ ] Implement client-to-GO connection flow (legacy WifiP2p clients connect to GO's socket)
- [ ] Implement GO's multi-client `ServerSocket` — accept multiple client connections
- [ ] Implement `RoutingTable` initial build — GO broadcasts its address, clients register
- [ ] Test: Device A (GO), Device B and Device C (clients) — all three can exchange HELLO packets

**Deliverable:** 3-device WiFi Direct group with routing table populated on all nodes.

---

### Week 14 — Single-Hop Communication (Mode 2)
**Goal:** Chat and file transfer work within the WiFi Direct group (all nodes in range).

- [ ] Reuse Mode 1 chat and file transfer on top of the Mode 2 socket infrastructure
- [ ] Routing decision: if destination is a direct peer, send directly (hop count = 1)
- [ ] Test: Device B → Device C message (both connected to GO = Device A) — message routes through A
- [ ] Verify packet TTL is correctly set and decremented

**Deliverable:** All Mode 1 features work in a 3-node, single-hop Mode 2 topology.

---

### Week 15 — Multi-Hop Routing (AODV-Inspired)
**Goal:** Packets reach nodes that are not directly connected.

- [ ] Implement RREQ (Route Request) broadcast — flooded with hop limit
- [ ] Implement RREP (Route Reply) — unicast back along reverse path
- [ ] Implement `RoutingTable` update on RREP receipt
- [ ] Implement `HopManager.forward()` — decrement TTL, update next-hop, relay packet
- [ ] Physical test setup: Device A — Device B — Device C (A and C out of direct range, B is relay)
- [ ] Verify Device A can send a message to Device C via Device B

**Deliverable:** Multi-hop routing works across 3 physical devices with Device B acting as relay.

---

### Week 16 — Route Maintenance & Failure Recovery
**Goal:** Network reroutes automatically when a relay node drops.

- [ ] Implement RERR (Route Error) packet — triggered when a next-hop is unreachable
- [ ] Implement route invalidation — mark affected routes stale in `RoutingTable`
- [ ] Implement RREQ retry with backoff on route failure
- [ ] Physical test: 4-device topology A–B–C–D; pull Device C; verify A can still reach D via a new path
- [ ] Implement periodic Hello beacons (every 5 seconds) to detect silent node failures

**Deliverable:** Network heals within 15 seconds of a relay node going offline.

---

### Week 17 — Mode 1 ↔ Mode 2 Automatic Transition
**Goal:** App detects internet/router loss and switches to Disaster Mode without user action.

- [ ] Implement `ModeController` — monitors `ConnectivityManager`, `WifiManager`
- [ ] Implement transition logic: LAN loss detected → `ModeController` triggers Mode 2 init
- [ ] Implement manual override: `ModeToggleScreen` allows user to force-switch
- [ ] UI indicator: status bar chip shows current mode (green WiFi icon vs. red mesh icon)
- [ ] Test: Connected on Mode 1 → physically disconnect from router → app auto-switches to Mode 2 within 10 seconds

**Deliverable:** Seamless automatic mode transition verified on physical devices.

---

### Week 18 — Stress Testing & Stability (Mode 2)
**Goal:** Mode 2 holds for 60+ minutes of continuous use with 4 devices.

- [ ] Run 60-minute continuous chat session across 4 devices with multi-hop topology
- [ ] Measure and log: message delivery rate, average latency, routing table size over time
- [ ] Test file transfer over multi-hop (50 MB file, A→C with B as relay)
- [ ] Fix memory leaks: profile with Android Studio Memory Profiler, look for retained socket objects
- [ ] Implement periodic routing table pruning — remove stale entries older than 30 seconds

**Deliverable:** Mode 2 stability test passes. No crashes or memory growth over 60 minutes.

---

### Week 19 — Permission System & UI Polish
**Goal:** Permission enforcement works correctly; UI is presentable.

- [ ] Enforce VIEW_ONLY permission: recipient cannot download, only preview in memory
- [ ] Implement DOWNLOADABLE permission: file saved to `Downloads/HyperShare/`
- [ ] Polish all screens to match `Theme.md` — consistent glassmorphism styling
- [ ] Add onboarding flow: first-launch permission explanations (Location, WiFi, Storage)
- [ ] Accessibility pass: content descriptions on all icons, minimum tap target 48 dp

**Deliverable:** Permission system enforced. UI matches design doc.

---

### Week 20 — Full Integration & Pre-Testing Cleanup
**Goal:** Both modes are integrated and all known bugs are fixed before final testing.

- [ ] Full regression: run all `Testing_Scenarios.md` test cases manually
- [ ] Fix all P1 and P2 bugs
- [ ] Finalize `RoutingDebugScreen` for demo use (show live routing table, hop counts)
- [ ] Code cleanup: remove TODOs, dead code, debug print statements
- [ ] Performance: measure app startup time (<3 seconds to peer list), first discovery time

**Deliverable:** Both modes pass regression. App is demo-ready.

---

## Phase 3 — Testing, Documentation & Submission (Weeks 21–24)

### Week 21–22 — Formal Testing (see `Testing_Scenarios.md`)
- Run all Mode 1 and Mode 2 test scenarios with full documentation
- Physical multi-hop tests with at least 4 devices in real outdoor/building scenarios
- Document results: pass/fail, latency measurements, edge cases found

### Week 23 — Research Paper & Poster Draft
- Write abstract, introduction, protocol specification, and routing algorithm formally
- Prepare performance metrics table and build project poster

### Week 24 — Final Revision, Demo Practice & Submission
- Practice live demo script (Mode 1 → Mode 2 switch → multi-hop messaging)
- Repository cleanup, final tag, release build, and submission

---

## Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| WifiP2pManager instability on specific Android OEMs | High | Test on 3+ OEM devices early (Week 10); document known bad devices |
| Multi-hop routing loops | Medium | TTL cap (max 8 hops) + sequence number deduplication |
| Android Doze killing Foreground Services | Medium | Acquire `WifiLock` + `PowerManager.PARTIAL_WAKE_LOCK` in service |
| GO role flapping in WiFi Direct | High | Implement GO stability scoring; prefer device with most connections as GO |
| File descriptor leak under load | Medium | Stress test in Week 16; use `Closeable` + try-with-resources everywhere |
| Team member unavailability | Low | All design docs kept in repo; no single point of knowledge failure |
