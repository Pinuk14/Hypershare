# HyperShare — Protocol Specification v1.0

> Custom lightweight binary protocol for peer-to-peer communication over local networks and WiFi Direct mesh.  
> All multi-byte integers are **Big-Endian** unless noted otherwise.

---

## 1. Design Goals

| Goal | Decision |
|---|---|
| Minimize overhead | Fixed-length headers. No XML, JSON, or text framing. |
| Mobile-optimized chunk size | 64 KB data chunks — balances throughput vs. memory on low-RAM devices |
| Support multi-hop routing | Routing fields (source, destination, TTL) present in every packet |
| Distinguish Disaster Mode packets | A `FLAGS` byte with a dedicated `DISASTER_MODE` bit |
| Streaming without full download | A dedicated STREAM packet type with sequence numbers for ordering |
| Forward compatibility | A `VERSION` byte in every header |

---

## 2. Packet Structure — General Layout

Every HyperShare packet has two sections:

```
┌─────────────────────────────────────────────────────────────────────┐
│                          ROUTING HEADER                              │
│        (Fixed 32 bytes — visible to relay nodes for routing)         │
├─────────────────────────────────────────────────────────────────────┤
│                       ENCRYPTED PAYLOAD                              │
│             (Variable length — opaque to relay nodes)                │
└─────────────────────────────────────────────────────────────────────┘
```

Relay nodes read only the Routing Header. They never decrypt the payload.

---

## 3. Routing Header (32 bytes, fixed)

```
Offset  Size  Field             Type        Description
──────  ────  ────────────────  ──────────  ─────────────────────────────────────────────
0       1     MAGIC             uint8       Always 0xHS = 0x48 0x53. Identifies HyperShare packet.
              (2 bytes total)   
1       1     MAGIC[1]          uint8       0x53
2       1     VERSION           uint8       Protocol version. Current: 0x01
3       1     PACKET_TYPE       uint8       See Section 4 — Packet Type Registry
4       1     FLAGS             uint8       See Section 5 — Flags Byte
5       1     TTL               uint8       Time-to-live in hops. Max: 0x08 (8). Decremented by each relay.
6       2     PACKET_LENGTH     uint16      Total packet length in bytes (header + payload). Max: 65535 bytes.
8       8     SOURCE_ID         bytes[8]    First 8 bytes of the originating device's UUID
16      8     DESTINATION_ID    bytes[8]    First 8 bytes of the destination device's UUID.
                                            0xFF×8 = broadcast to all reachable nodes.
24      4     SEQUENCE_NUMBER   uint32      Monotonically increasing per (source, type) pair. Used for dedup.
28      4     SESSION_ID        uint32      Identifies the application session (transfer ID, chat session ID, stream ID).
                                            0x00000000 for control packets with no session context.
```

**Total header size: 32 bytes.**

---

## 4. Packet Type Registry

| Type Byte | Name | Direction | Description |
|---|---|---|---|
| `0x01` | `HELLO` | Broadcast | Peer announcement and GO candidacy. First packet on any connection. |
| `0x02` | `HELLO_ACK` | Unicast | Response to HELLO. Completes handshake. |
| `0x03` | `MSG` | Unicast | Text message. |
| `0x04` | `MSG_ACK` | Unicast | Acknowledgement of text message receipt. |
| `0x05` | `DATA_CHUNK` | Unicast | One chunk of a file transfer. |
| `0x06` | `DATA_ACK` | Unicast | Acknowledgement of a received DATA_CHUNK. |
| `0x07` | `DATA_NACK` | Unicast | Negative ACK — chunk CRC32 mismatch, request retransmit. |
| `0x08` | `STREAM_FRAME` | Unicast | One frame of a media stream. |
| `0x09` | `STREAM_ACK` | Unicast | Stream frame received (flow control). |
| `0x0A` | `RREQ` | Broadcast | Route Request — initiate route discovery. |
| `0x0B` | `RREP` | Unicast | Route Reply — confirm route to destination. |
| `0x0C` | `RERR` | Broadcast | Route Error — notify affected nodes of broken route. |
| `0x0D` | `DISCONNECT` | Unicast | Graceful peer disconnect notification. |
| `0x0E` | `PING` | Unicast | Keep-alive / latency measurement. |
| `0x0F` | `PONG` | Unicast | Response to PING. |
| `0x10` | `TRANSFER_INIT` | Unicast | Announces an upcoming file transfer (metadata before chunks). |
| `0x11` | `TRANSFER_COMPLETE` | Unicast | Signals all chunks for a transfer have been sent. |
| `0x12` | `STREAM_INIT` | Unicast | Announces an upcoming stream (codec, bitrate metadata). |
| `0x13` | `STREAM_END` | Unicast | Stream has ended. |
| `0xFF` | `ERROR` | Unicast | Generic error response with error code in payload. |

---

## 5. Flags Byte (1 byte, 8 bits)

```
Bit 7 (MSB)  DISASTER_MODE    1 = packet is being sent in Disaster Mode (Mode 2)
                               0 = packet is being sent in WiFi LAN Mode (Mode 1)
Bit 6        RELAY_PACKET     1 = this packet has been relayed at least once (not from origin)
                               0 = packet is from its original source
Bit 5        ENCRYPTED        1 = payload is AES-256-GCM encrypted (always 1 in production)
                               0 = payload is plaintext (debug builds only)
Bit 4        COMPRESSED       1 = payload is zlib-compressed before encryption
                               0 = payload is uncompressed
Bit 3        BROADCAST        1 = intended for all reachable nodes (DESTINATION_ID = 0xFF×8)
                               0 = unicast
Bit 2        VIEW_ONLY        1 = received data must not be written to disk (streaming/view-only permission)
                               0 = recipient may save received data
Bit 1        FRAGMENTED       1 = this is a fragment of a larger logical packet (unused in v1)
                               0 = complete packet
Bit 0 (LSB)  RESERVED         Must be 0. Reserved for future use.
```

**Example FLAGS values:**
- `0b10100000` = `0xA0` — Disaster Mode, not relayed, encrypted, uncompressed, unicast, downloadable
- `0b10110100` = `0xB4` — Disaster Mode, relayed, encrypted, uncompressed, unicast, view-only

---

## 6. Payload Formats by Packet Type

All payloads are AES-256-GCM encrypted. The format below describes the **plaintext** before encryption.

---

### 6.1 HELLO (`0x01`) — 48 bytes payload

```
Offset  Size  Field              Type      Description
0       16    DEVICE_UUID        bytes[16] Full 128-bit UUID of sender
16      20    DISPLAY_NAME       bytes[20] UTF-8, null-padded. Device's human-readable name.
36      1     GO_SCORE           uint8     Group Owner candidacy score (0–100). See Mesh_Routing_Logic.md.
37      1     BATTERY_PCT        uint8     Battery percentage (0–100).
38      1     CURRENT_MODE       uint8     0x01 = Mode 1, 0x02 = Mode 2
39      1     PEER_COUNT         uint8     Number of peers this device is currently connected to.
40      4     APP_VERSION_CODE   uint32    App versionCode for compatibility check.
44      4     CAPABILITIES       uint32    Bitmask of supported features (reserved for future; send 0x00000001 for v1).
```

---

### 6.2 HELLO_ACK (`0x02`) — 20 bytes payload

```
Offset  Size  Field              Type      Description
0       16    DEVICE_UUID        bytes[16] Full UUID of responding device.
16      2     ASSIGNED_PORT      uint16    Port the responder's data server is listening on (default 47200).
18      1     ACCEPT             uint8     0x01 = connection accepted, 0x00 = rejected.
19      1     REJECT_REASON      uint8     0x00 = N/A, 0x01 = version mismatch, 0x02 = at capacity.
```

---

### 6.3 MSG (`0x03`) — Variable payload

```
Offset  Size  Field              Type      Description
0       4     MSG_LENGTH         uint32    Length of the message body in bytes.
4       N     MSG_BODY           bytes[N]  UTF-8 encoded message text. Max 4096 bytes.
4+N     8     TIMESTAMP          int64     Unix timestamp in milliseconds (sender's clock).
```

Max message size: 4096 bytes (enforced by UI; split into multiple MSG packets if exceeded in v2).

---

### 6.4 MSG_ACK (`0x04`) — 12 bytes payload

```
Offset  Size  Field              Type      Description
0       4     ACK_SEQ_NUM        uint32    SEQUENCE_NUMBER from the MSG packet being acknowledged.
4       8     RECV_TIMESTAMP     int64     Receiver's Unix timestamp when packet arrived.
```

---

### 6.5 TRANSFER_INIT (`0x10`) — Variable payload

Sent before the first DATA_CHUNK. Receiver uses this to pre-allocate space and show UI.

```
Offset  Size  Field              Type      Description
0       4     TRANSFER_ID        uint32    Unique ID for this transfer. Matches SESSION_ID in all subsequent chunks.
4       8     TOTAL_SIZE         int64     Total file size in bytes.
12      4     TOTAL_CHUNKS       uint32    Total number of chunks. (totalSize / CHUNK_SIZE, ceiling-rounded)
16      4     CRC32_FULL         uint32    CRC32 of the complete file. Verified after reassembly.
20      1     PERMISSION         uint8     0x01 = VIEW_ONLY, 0x02 = DOWNLOADABLE
21      2     FILENAME_LENGTH    uint16    Length of filename string.
23      N     FILENAME           bytes[N]  UTF-8 filename. Max 255 bytes.
23+N    M     MIME_TYPE          bytes[M]  UTF-8 MIME type string, null-terminated. Max 64 bytes.
```

---

### 6.6 DATA_CHUNK (`0x05`) — Variable payload (max ~65.5 KB)

```
Offset  Size  Field              Type      Description
0       4     TRANSFER_ID        uint32    Matches TRANSFER_INIT's SESSION_ID.
4       4     CHUNK_INDEX        uint32    Zero-based chunk index (0 = first chunk).
8       2     CHUNK_SIZE         uint16    Actual size of this chunk's data in bytes (last chunk may be < 65536).
10      4     CRC32_CHUNK        uint32    CRC32 of this chunk's data only.
14      N     CHUNK_DATA         bytes[N]  Raw chunk data. N = CHUNK_SIZE.
```

**Chunk size:** `CHUNK_SIZE = 65536 bytes (64 KB)`. Last chunk may be smaller.

**Sliding window:** Sender transmits up to `WINDOW_SIZE = 8` chunks before waiting for ACKs. Receiver ACKs each chunk individually.

---

### 6.7 DATA_ACK (`0x06`) — 8 bytes payload

```
Offset  Size  Field              Type      Description
0       4     TRANSFER_ID        uint32    Transfer this ACK belongs to.
4       4     CHUNK_INDEX        uint32    Index of the chunk being acknowledged.
```

---

### 6.8 DATA_NACK (`0x07`) — 9 bytes payload

```
Offset  Size  Field              Type      Description
0       4     TRANSFER_ID        uint32    Transfer this NACK belongs to.
4       4     CHUNK_INDEX        uint32    Index of the corrupted chunk (CRC32 mismatch).
8       1     REASON             uint8     0x01 = CRC32 mismatch, 0x02 = out of order, 0x03 = buffer full.
```

---

### 6.9 STREAM_INIT (`0x12`) — 32 bytes payload

```
Offset  Size  Field              Type      Description
0       4     STREAM_ID          uint32    Unique stream ID. Matches SESSION_ID in STREAM_FRAMEs.
4       1     CODEC              uint8     0x01 = H.264, 0x02 = H.265, 0x03 = VP8, 0x10 = MP3, 0x11 = AAC.
5       4     BITRATE            uint32    Target bitrate in bits per second.
9       2     WIDTH              uint16    Video width in pixels (0 for audio-only).
11      2     HEIGHT             uint16    Video height in pixels (0 for audio-only).
13      4     FRAME_RATE         uint32    Frames per second × 1000 (e.g., 29970 = 29.97 fps).
17      8     TOTAL_DURATION_MS  int64     Stream duration in ms. 0 = live/unknown.
25      1     PERMISSION         uint8     0x01 = VIEW_ONLY (cannot record), 0x02 = RECORDABLE.
26      6     RESERVED           bytes[6]  Reserved, set to 0x00.
```

---

### 6.10 STREAM_FRAME (`0x08`) — Variable payload

```
Offset  Size  Field              Type      Description
0       4     STREAM_ID          uint32    Identifies the stream.
4       4     FRAME_SEQ          uint32    Monotonically increasing frame sequence number.
8       8     PRESENTATION_TS    int64     Presentation timestamp in microseconds from stream start.
16      1     FRAME_TYPE         uint8     0x01 = I-frame (keyframe), 0x02 = P-frame, 0x03 = B-frame.
17      3     FRAME_SIZE         uint24    Size of frame data in bytes (3 bytes = up to 16 MB per frame).
20      N     FRAME_DATA         bytes[N]  Raw encoded frame data.
```

---

### 6.11 RREQ (`0x0A`) — 28 bytes payload

```
Offset  Size  Field              Type      Description
0       16    RREQ_UUID          bytes[16] Unique ID for this RREQ (for deduplication cache).
16      4     SRC_SEQ_NUM        uint32    Source's current sequence number.
20      4     DEST_SEQ_NUM       uint32    Last known sequence number for destination (0 if unknown).
24      4     RREQ_HOP_COUNT     uint32    Incremented by each forwarding node. Drop if > MAX_HOPS (8).
```

Note: SOURCE_ID and DESTINATION_ID in the Routing Header carry the src and dest UUIDs.

---

### 6.12 RREP (`0x0B`) — 16 bytes payload

```
Offset  Size  Field              Type      Description
0       4     DEST_SEQ_NUM       uint32    Destination's sequence number (freshness guarantee).
4       4     RREP_HOP_COUNT     uint32    Hop count from destination to current node. Starts at 0 at dest.
8       4     ROUTE_LIFETIME_MS  uint32    How long this route entry should be considered valid (ms).
12      4     RESERVED           uint32    Set to 0x00.
```

---

### 6.13 RERR (`0x0C`) — Variable payload

```
Offset  Size  Field              Type      Description
0       2     DEST_COUNT         uint16    Number of destination IDs whose routes are broken.
2       N×8   DEST_IDS           bytes[]   Array of 8-byte destination IDs that are now unreachable.
```

---

### 6.14 PING (`0x0E`) — 8 bytes payload

```
Offset  Size  Field              Type      Description
0       8     PING_TIMESTAMP     int64     Sender's timestamp in nanoseconds (for RTT calculation).
```

---

### 6.15 PONG (`0x0F`) — 16 bytes payload

```
Offset  Size  Field              Type      Description
0       8     ORIG_TIMESTAMP     int64     Echo of PING's timestamp.
4       8     PONG_TIMESTAMP     int64     Responder's timestamp in nanoseconds.
```

---

## 7. Encryption Envelope

All payloads are wrapped in an encryption envelope before transmission:

```
Offset  Size  Field           Description
0       12    IV              AES-GCM nonce. Randomly generated per packet.
12      N     CIPHERTEXT      AES-256-GCM encrypted plaintext payload.
12+N    16    AUTH_TAG        GCM authentication tag (16 bytes).
```

Total encryption overhead per packet: **28 bytes** (12 IV + 16 tag).

The AES key is derived from the ECDH shared secret using HKDF-SHA256:
```
aes_key = HKDF(ikm=ecdh_shared_secret, salt=session_id_bytes, info="hypershare-v1-aes", length=32)
```

---

## 8. Connection Lifecycle

```
Device A                                      Device B
    │                                              │
    │──── TCP connect to port 47200 ──────────────▶│
    │                                              │
    │──── HELLO (type=0x01) ──────────────────────▶│
    │     [public key in payload for ECDH]         │
    │                                              │
    │◀─── HELLO_ACK (type=0x02) ──────────────────│
    │     [public key in payload for ECDH]         │
    │                                              │
    │  [Both derive shared secret via ECDH]        │
    │  [All subsequent packets encrypted]          │
    │                                              │
    │──── PING (type=0x0E) ───────────────────────▶│
    │◀─── PONG (type=0x0F) ───────────────────────│
    │                                              │
    │  [Normal operation: MSG, DATA_CHUNK, etc.]   │
    │                                              │
    │──── DISCONNECT (type=0x0D) ─────────────────▶│
    │                                              │
    │  [TCP connection closed]                     │
```

---

## 9. Port Assignments

| Port | Protocol | Purpose |
|---|---|---|
| `47200` | TCP | Main data channel (all packet types after handshake) |
| `47201` | UDP | Pre-connection HELLO broadcast (GO election, Mode 2 only) |
| `47202` | UDP | mDNS supplemental discovery ping (Mode 1 only) |

---

## 10. Versioning & Compatibility

- `VERSION` field in header is `0x01` for this specification.
- On HELLO receipt: if receiver's app cannot support sender's version, send `HELLO_ACK` with `ACCEPT=0x00`, `REJECT_REASON=0x01`.
- Minor additions (new packet types `0x14+`) are backward-compatible if old clients ignore unknown types.
- Breaking changes require a `VERSION` bump to `0x02`.
