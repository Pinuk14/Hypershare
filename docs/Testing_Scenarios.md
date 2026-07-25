# HyperShare — Testing Scenarios

> Covers Mode 1 (WiFi LAN) and Mode 2 (Disaster Mesh). Includes physical device tests, edge cases, and performance benchmarks.  
> All tests must be run on **physical devices**. Emulators cannot simulate WiFi Direct or multi-hop mesh topology.

---

## Device Lab Setup

Label your physical devices before testing begins. Track their specs here:

| Label | Model | Android Version | RAM | Notes |
|---|---|---|---|---|
| Device A | ___ | ___ | ___ | Designated initial GO candidate |
| Device B | ___ | ___ | ___ | |
| Device C | ___ | ___ | ___ | |
| Device D | ___ | ___ | ___ | Out-of-range relay target |

**Minimum requirement:** 3 physical devices for Mode 1, 4 physical devices for multi-hop Mode 2 tests.

---

## Test Result Format

For each test case, record:

```
Test ID: [e.g., M1-TC-01]
Status: PASS / FAIL / PARTIAL
Date:
Devices used:
Actual result:
Deviation from expected (if any):
Bug filed: [GitHub issue # or N/A]
```

---

## Part A — Mode 1: WiFi Local Network Tests

### Prerequisites
- All devices connected to the same WiFi router/hotspot.
- No active internet is required — a router without WAN is fine (a portable travel router works well for controlled testing).
- ADB logging enabled: `adb logcat -s HyperShare` on a connected laptop.

---

### M1-TC-01: Peer Discovery

**Scenario:** App discovers all devices on the same LAN automatically.

**Steps:**
1. Install HyperShare on Device A and Device B.
2. Launch app on both devices.
3. Navigate to `PeerListScreen` on both.
4. Wait up to 30 seconds.

**Expected:**
- Device A shows Device B in the peer list (with correct display name).
- Device B shows Device A in the peer list.
- Status chip shows `WIFI` in Signal Blue.
- Discovery time < 10 seconds on a standard router.

**Edge case to note:** If router has AP isolation enabled, devices cannot discover each other. Test with AP isolation explicitly OFF.

---

### M1-TC-02: Encrypted Handshake

**Scenario:** Two peers complete ECDH key exchange and establish an encrypted session.

**Steps:**
1. On Device A, tap Device B in the peer list.
2. Observe ADB logs for ECDH key exchange completion.
3. On Device B, confirm Device A appears as "CONNECTED."

**Expected:**
- Log shows: `[HANDSHAKE] ECDH shared secret derived for peer <PeerID>`
- Log shows: `[HANDSHAKE] Shared secret matches (first 8 bytes): <hex>` — same value on both devices.
- Both devices show each other as CONNECTED within 3 seconds of tap.

**How to verify:** Log the first 8 bytes of the derived AES key (never the full key) on both sides. They must match.

---

### M1-TC-03: Text Messaging

**Scenario:** Bidirectional real-time messaging between two peers.

**Steps:**
1. Establish connection between Device A and Device B (M1-TC-02 complete).
2. On Device A → ChatScreen with Device B → send "Hello from A."
3. On Device B → verify message appears.
4. On Device B → send "Reply from B."
5. On Device A → verify reply appears.
6. Measure round-trip time (send → receive log timestamps).

**Expected:**
- Messages appear on both sides within 200 ms on a local LAN.
- Message delivery status: sent (1 gray tick) → received (2 teal ticks).
- No message loss over 50 consecutive messages sent in rapid succession.

---

### M1-TC-04: File Transfer — Small File

**Scenario:** A 5 MB file transfers correctly with integrity verification.

**Steps:**
1. On Device A → FileBrowserScreen → select a 5 MB image file → set permission = DOWNLOADABLE → send to Device B.
2. On Device B → accept incoming transfer.
3. After completion, verify file appears in `Downloads/HyperShare/` on Device B.
4. Calculate CRC32 of the original file on Device A and of the received file on Device B. They must match.

**Expected:**
- Transfer completes in < 5 seconds on a standard 802.11n WiFi network.
- CRC32 of original == CRC32 of received.
- Progress bar on both devices reaches 100%.

**Script for CRC32 verification (run on laptop via ADB):**
```bash
# On sender
adb -s <deviceA_serial> shell "cksum /sdcard/testfile.mp4"
# On receiver
adb -s <deviceB_serial> shell "cksum /sdcard/Download/HyperShare/testfile.mp4"
# Values must match
```

---

### M1-TC-05: File Transfer — Large File

**Scenario:** A 250 MB file transfers reliably with chunk acknowledgement.

**Steps:**
1. Same as M1-TC-04 but with a 250 MB video file.
2. During transfer, observe chunk ACKs in logs (`DATA_ACK` packets logged per chunk).
3. Allow the full transfer to complete.

**Expected:**
- Transfer completes with 0 DATA_NACK retransmissions (on a clean LAN).
- If any NACKs occur, verify the affected chunks are retransmitted and the final file is intact.
- Transfer speed ≥ 5 MB/s on 802.11n.

---

### M1-TC-06: VIEW_ONLY Permission Enforcement

**Scenario:** A file sent with VIEW_ONLY permission is not saved to the receiver's storage.

**Steps:**
1. Send a 10 MB PDF from Device A to Device B with permission = VIEW_ONLY.
2. On Device B, the PDF opens for preview in `FileBrowserScreen`.
3. Check `Downloads/HyperShare/` on Device B — the file must NOT be present.
4. Attempt to long-press the file preview → no "Save" option should appear.

**Expected:**
- File previews correctly on Device B.
- File does NOT appear in `Downloads/HyperShare/` or any other storage path on Device B.
- ADB shell: `adb -s <deviceB> shell "find /sdcard -name '<filename>'"` returns empty.

---

### M1-TC-07: Media Streaming

**Scenario:** A 2-minute video streams to another device without being downloaded.

**Steps:**
1. On Device A → FileBrowserScreen → select a 2-minute MP4 → set permission = VIEW_ONLY → start stream to Device B.
2. On Device B → `StreamPlayerScreen` opens automatically.
3. Allow playback to complete.
4. Verify no file was saved on Device B (same check as M1-TC-06).

**Expected:**
- Video begins playing on Device B within 3 seconds of stream start.
- No significant buffering pauses (< 2 pauses for a 2-minute video on a clean LAN).
- No file on Device B's storage.

---

### M1-TC-08: Multi-Peer Simultaneous Connection

**Scenario:** Three devices all connected simultaneously, each can message all others.

**Steps:**
1. Connect Device A, B, and C all to the same router.
2. A connects to B. A connects to C. B connects to C.
3. A sends a message to B AND C simultaneously.
4. B sends a file to A.

**Expected:**
- All three connections stable simultaneously.
- No deadlock or resource contention.
- Messages deliver to both B and C.
- File transfer from B completes alongside A's outgoing messages.

---

### M1-TC-09: Transfer Resume After Disconnect

**Scenario:** A file transfer in progress survives a temporary network disconnect.

**Steps:**
1. Begin transferring a 100 MB file from A to B.
2. When transfer reaches ~40%, disconnect Device B from WiFi for 10 seconds, then reconnect.
3. Observe app behavior on both devices.

**Expected:**
- Transfer pauses on both sides during disconnect.
- Transfer resumes from last ACKed chunk after reconnect (not from the beginning).
- Final file is complete and CRC32-verified.

**Acceptable:** If resume does not work in v1, the transfer restarts from the beginning. Document this as a known limitation in the paper.

---

## Part B — Mode 2: Disaster Mesh Network Tests

### Prerequisites
- WiFi Direct works on all test devices (not all Android OEMs implement it fully).
- `ACCESS_FINE_LOCATION` permission granted on all devices (required for WifiP2p on Android 10+).
- Testing location: an **open area** (parking lot, open field) works better than a room with concrete walls for controlled range testing.
- Carry a portable battery bank — tests require devices to be mobile.

---

### M2-TC-01: Two-Device WiFi Direct Group Formation

**Scenario:** Two devices form a stable WiFi Direct group with correct GO assignment.

**Steps:**
1. Disable WiFi LAN on both devices (forget all networks, toggle to Mode 2 in app).
2. Launch HyperShare on Device A (higher battery) and Device B.
3. GO election should assign Device A as GO.
4. Wait 30 seconds after group formation.

**Expected:**
- Group forms within 20 seconds.
- Device A's logs show `[GO] Became Group Owner. IP: 192.168.49.1`
- Device B's logs show `[CLIENT] Connected to GO. GO IP: 192.168.49.1`
- GO_SCORE of Device A > GO_SCORE of Device B in logs.
- Group remains stable for 5 minutes with no drops.

---

### M2-TC-02: Three-Device Group — Single Hop Communication

**Scenario:** Three devices form a group; Device A (GO) routes messages between B and C.

**Physical setup:** All three devices within WiFi Direct range (< 30 meters).

**Steps:**
1. Form group: A=GO, B=client, C=client.
2. Device B sends a text message to Device C.
3. Observe routing in logs: message should travel B → A → C (A acts as relay even though it's the router, not the logical destination).
4. Device C replies to Device B.

**Expected:**
- Message from B arrives at C within 500 ms.
- Logs on Device A show `[RELAY] Forwarding MSG from B to C, TTL: 7`
- Delivery ticks on Device B update: sent → relayed (via A) → received.
- `ChatScreen` on B shows `"via 1 hop"` under the message thread header.

---

### M2-TC-03: Multi-Hop Routing — Physical Distance Test ⭐

> **This is the most important and most physically demanding test in the project.**

**Scenario:** Device A and Device D communicate via Device B (relay), with A and D physically out of each other's direct WiFi Direct range.

**Physical setup:**
```
[Device A] ←——— ~40m ———→ [Device B] ←——— ~40m ———→ [Device D]
(Sender)                   (Relay)                   (Receiver)

A and D are ~80m apart — outside WiFi Direct range of each other.
B is midpoint, in range of both.
```

**Steps:**
1. Form group: B = GO (highest score for this test — ensure B has best battery).
2. Confirm A and D are connected as clients to B.
3. On Device A, send a message to Device D.
4. Observe: A cannot reach D directly. A sends to B (next hop). B forwards to D.

**Expected:**
- Message arrives at Device D.
- Logs on D show `[RECV] MSG from A (2 hops)`
- Routing table on A shows: `dest=D, nextHop=B, hopCount=2`
- Routing table on B shows: `dest=D, nextHop=D, hopCount=1` and `dest=A, nextHop=A, hopCount=1`
- Delivery time: < 1 second.

**Verification:** After the test, capture routing table screenshots from all three devices' `RoutingDebugScreen`.

---

### M2-TC-04: RREQ / RREP Route Discovery Trace

**Scenario:** Verify that RREQ broadcast and RREP unicast are behaving as specified.

**Steps:**
1. Same physical setup as M2-TC-03.
2. Clear routing tables on all devices (dev menu option).
3. On Device A, attempt to send a message to Device D.
4. Watch ADB logs on all three devices simultaneously (use `adb logcat -s HyperShare` on 3 terminal windows).

**Expected log sequence on Device A:**
```
[ROUTING] No route to D. Initiating RREQ.
[ROUTING] RREQ broadcast: rreqId=<uuid>, src=A, dest=D, hopCount=0
[ROUTING] RREP received from B: dest=D, nextHop=B, hopCount=2
[ROUTING] Route to D established: via B (2 hops)
[SEND] Sending MSG to D via next hop B
```

**Expected log sequence on Device B:**
```
[ROUTING] RREQ received from A: dest=D, hopCount=0
[ROUTING] I have route to D (direct, 1 hop). Sending RREP to A.
[ROUTING] RREP sent to A: dest=D, hopCount=1
[RELAY] Forwarding MSG from A to D
```

---

### M2-TC-05: Node Failure & Route Recovery

**Scenario:** Remove a relay node mid-communication and verify the network heals.

**Physical setup (4 devices):**
```
[Device A] ——→ [Device B (relay)] ——→ [Device C (relay)] ——→ [Device D]
```

**Steps:**
1. Form group. Establish multi-hop communication A → D (routing through B and C).
2. Verify A can message D successfully.
3. **Walk Device B out of range (or power it off).**
4. Wait. Observe healing behavior.
5. After healing, attempt to send another message from A to D.

**Expected:**
- After B is lost, within 15 seconds:
  - B's neighbors (A, C) detect missing HELLO beacons.
  - RERR packet broadcast from A and C for routes through B.
  - RREQ initiated by A for new route to D.
  - New route A → C → D established (if C is in range of A).
- Message sent after healing successfully reaches D via the new route.
- Acceptable healing time: 15–30 seconds.

**Document the actual healing time measured.**

---

### M2-TC-06: Automatic Mode Transition

**Scenario:** App auto-switches from Mode 1 to Mode 2 when LAN connectivity is lost.

**Steps:**
1. Start app in Mode 1. All devices on same WiFi router. Confirm chat works.
2. Power off the WiFi router.
3. Wait and observe both devices.
4. Power the router back on after 2 minutes.

**Expected:**
- Within 10 seconds of router loss: Mode chip on both devices switches from `WIFI` to `MESH`.
- WiFi Direct group forms within 30 seconds of mode switch.
- After router returns: app does NOT auto-switch back (user must manually switch back to Mode 1 to prevent disruption during an active disaster mesh session).

---

### M2-TC-07: TTL Enforcement (Anti-Loop Test)

**Scenario:** Verify that packets with TTL=0 are dropped and not forwarded infinitely.

**Steps:**
1. 3-device linear topology: A → B → C.
2. Using `RoutingDebugScreen` on a debug build, manually inject a test packet with TTL=1 destined for a non-existent node (PeerID "FFFFFFFF").
3. Observe behavior.

**Expected:**
- Device B receives the packet (TTL decremented to 0).
- Device B does NOT forward the packet.
- Device B logs: `[HOP] TTL expired. Dropping packet destined for FFFFFFFF.`
- No infinite forwarding loop.

---

### M2-TC-08: GO Failover

**Scenario:** The Group Owner device loses power and the group reforms with a new GO.

**Steps:**
1. Form a 3-device group: A=GO, B=client, C=client.
2. Confirm messaging works between B and C.
3. Power off Device A (or walk it out of range).
4. Wait and observe B and C.

**Expected:**
- B and C detect GO loss within 15 seconds (3 missed HELLO beacons × 5s interval).
- B and C begin new GO election.
- New group forms with B or C as GO within 30–45 seconds.
- B and C can message each other after the new group is formed.

**Known issue to document:** GO failover time in WiFi Direct is inherently slow (30–60 seconds) due to Android's reconnection logic. This is a platform limitation, not a bug.

---

### M2-TC-09: File Transfer Over Multi-Hop

**Scenario:** A 50 MB file transfers reliably from A to D over a 2-hop relay.

**Physical setup:** Same as M2-TC-03.

**Steps:**
1. Establish A → B → D route.
2. From Device A, send a 50 MB file (DOWNLOADABLE) to Device D.
3. Wait for full transfer.
4. Verify CRC32 of received file on D matches original on A.

**Expected:**
- Transfer completes successfully (may be slow — 50 MB over WiFi Direct relay can take 3–10 minutes depending on hardware).
- CRC32 matches.
- Intermediate node (B) does not save any portion of the file to its own storage.

---

### M2-TC-10: 60-Minute Stability Test

**Scenario:** Mode 2 mesh holds stable for 60 minutes under continuous use.

**Steps:**
1. Form 4-device group.
2. Send 1 message per minute between random pairs.
3. Send 1 small file (5 MB) every 10 minutes.
4. Every 15 minutes, record: memory usage (Android Studio Profiler), routing table entry count, any crashes.

**Expected:**
- Zero crashes over 60 minutes.
- No memory growth trend (routing table pruning keeps it bounded).
- All messages delivered successfully.
- Log file saved and reviewed for any ERROR-level entries.

---

## Part C — Edge Cases & Regression

| Test ID | Scenario | Pass Condition |
|---|---|---|
| EDGE-01 | Send a 0-byte file | Graceful error message, no crash |
| EDGE-02 | Send a file with a 255-character filename | Correctly transmitted and saved |
| EDGE-03 | Destination device is full (no storage) | Sender shows "Transfer failed: storage full" error |
| EDGE-04 | Two devices try to connect to each other simultaneously | Only one connection is established; no duplicate socket |
| EDGE-05 | Peer sends a malformed packet (wrong magic bytes) | Packet dropped silently, connection maintained |
| EDGE-06 | AES-GCM auth tag fails (tampered packet) | Packet dropped, `[SECURITY] Auth tag mismatch` logged |
| EDGE-07 | App sent to background during transfer | Transfer continues in foreground service; no interruption |
| EDGE-08 | Device receives its own broadcast RREQ | RREQ dropped (source ID == self ID check) |
| EDGE-09 | TTL exceeds MAX_HOP_COUNT (8) on arrival | Packet dropped immediately |
| EDGE-10 | Two devices have identical display names | Differentiated by DeviceID; no confusion in routing |

---

## Part D — Performance Benchmarks (Record Actual Values)

Run these after stability tests pass. Record actual measured values in your paper.

| Metric | Target | Measured (fill in) |
|---|---|---|
| Mode 1 peer discovery time | < 10 seconds | |
| Mode 2 group formation time | < 30 seconds | |
| Mode 1 message latency (LAN) | < 200 ms | |
| Mode 2 message latency (direct peer) | < 500 ms | |
| Mode 2 message latency (2-hop relay) | < 1000 ms | |
| File transfer throughput (Mode 1, 802.11n) | ≥ 5 MB/s | |
| File transfer throughput (Mode 2, direct) | ≥ 1 MB/s | |
| Route discovery time (RREQ→RREP) | < 3 seconds | |
| GO failover time | < 60 seconds | |
| Mode 1→2 auto-transition time | < 10 seconds | |
| Memory footprint (idle) | < 80 MB RSS | |
| Memory footprint (active transfer) | < 150 MB RSS | |

---

## Testing Log Template

Copy this block for every test session:

```
Session Date:
Team Members Present:
Devices Used (model, Android version):
Location:
Build Version / Git Commit:

Tests Run:
[ ] M1-TC-01  [ ] M1-TC-02  [ ] M1-TC-03  [ ] M1-TC-04
[ ] M1-TC-05  [ ] M1-TC-06  [ ] M1-TC-07  [ ] M1-TC-08
[ ] M1-TC-09
[ ] M2-TC-01  [ ] M2-TC-02  [ ] M2-TC-03  [ ] M2-TC-04
[ ] M2-TC-05  [ ] M2-TC-06  [ ] M2-TC-07  [ ] M2-TC-08
[ ] M2-TC-09  [ ] M2-TC-10

Bugs Found This Session:
1.
2.

Notable Observations:

ADB Log File Saved To: [path]
```
