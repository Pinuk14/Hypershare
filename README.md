<div align = "center">

```
██╗  ██╗██╗   ██╗██████╗ ███████╗██████╗ ███████╗██╗  ██╗ █████╗ ██████╗ ███████╗
██║  ██║╚██╗ ██╔╝██╔══██╗██╔════╝██╔══██╗██╔════╝██║  ██║██╔══██╗██╔══██╗██╔════╝
███████║ ╚████╔╝ ██████╔╝█████╗  ██████╔╝███████╗███████║███████║██████╔╝█████╗  
██╔══██║  ╚██╔╝  ██╔═══╝ ██╔══╝  ██╔══██╗╚════██║██╔══██║██╔══██║██╔══██╗██╔══╝  
██║  ██║   ██║   ██║     ███████╗██║  ██║███████║██║  ██║██║  ██║██║  ██║███████╗
╚═╝  ╚═╝   ╚═╝   ╚═╝     ╚══════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝
```

**Infraless Disaster Communication System using Local Mesh Networks**

*B.Tech Final Year Project*

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Protocol](https://img.shields.io/badge/Protocol-Custom%20Binary%20v1.0-FF6B35)](./Protocol_Specs.md)
[![License](https://img.shields.io/badge/License-MIT-green)](./LICENSE)

</div>

---

## What is HyperShare?

HyperShare is a mobile-first, serverless communication and file-sharing system that works **entirely without the internet**. When disaster strikes and cell towers fail, HyperShare turns a group of Android phones into a self-organizing mesh network — phones discover each other, relay messages across multiple hops, and transfer files, all over local WiFi.

It operates in two modes:

| Mode | When to use | Technology |
|---|---|---|
| **Mode 1 — Local WiFi** | Router exists but no internet | mDNS discovery + TCP over LAN |
| **Mode 2 — Disaster Mesh** | All infrastructure gone | WiFi Direct + AODV-inspired multi-hop routing |

Switching is automatic. If HyperShare detects the LAN is gone, it transitions to mesh mode without any user action.

---

## The Problem It Solves

During floods, earthquakes, or any large-scale infrastructure failure, the first thing to go is mobile network coverage — precisely when communication matters most. Existing tools either require the internet (WhatsApp, Telegram) or are too complex to deploy quickly (dedicated mesh radio hardware).

HyperShare runs on the Android phone already in your pocket, requires no setup beyond installation, and can relay a message **across kilometers** by chaining devices as relay nodes.

---

## Features

- **Peer Discovery** — finds other HyperShare devices automatically via mDNS/UDP on LAN or Wi-Fi Direct in Disaster Mode
- **Wi-Fi Direct P2P Mesh (SIH Prototype)** — direct device-to-device discovery, client-to-GO socket formation, and encrypted HELLO handshake without any router or infrastructure
- **End-to-End Encrypted Messaging** — AES-256-GCM with ECDH key exchange; relay nodes cannot read content they forward
- **Contact-Gated Mutual Trust** — camera and gallery QR code identity exchange, device-stable identity (`BLAKE2b-256`), and Ed25519 cryptographic signatures
- **Permanent & Temporary Group Communication** — multi-peer group chatrooms, O(N) direct packet distribution, 3-stage delivery ticks (Sent, Delivered, Read), and deterministic avatar stacks
- **High-Speed File Transfer** — chunked transfer with CRC32 integrity verification and sliding-window ACKs
- **Media Streaming** — stream video/audio directly to another device without saving to disk
- **Permission Control** — share files as VIEW_ONLY (no download allowed) or DOWNLOADABLE
- **Multi-Hop Routing** — messages traverse devices that are out of direct radio range
- **Automatic Mode Switching** — detects infrastructure loss and switches to emergency mesh seamlessly
- **Works Offline** — zero internet dependency, zero cloud, zero servers

---

## Architecture at a Glance

```
┌─────────────────────────────────────────┐
│           UI  (Jetpack Compose)         │
│  PeerList · Chat · Files · Stream       │
└──────────────────┬──────────────────────┘
                   │ StateFlow / ViewModel
┌──────────────────▼──────────────────────┐
│          Application Layer              │
│  SessionManager · TransferQueue         │
└──────────────────┬──────────────────────┘
                   │ Coroutines
┌──────────────────▼──────────────────────┐
│       Foreground Services               │
│  MeshNetworkService · DiscoveryService  │
└────────┬─────────────────────┬──────────┘
         │                     │
┌────────▼──────────┐ ┌────────▼──────────┐
│  Routing Engine   │ │  Protocol Layer   │
│  AODV · TTL ·     │ │  Binary v1.0 ·    │
│  RoutingTable     │ │  PacketBuilder    │
└───────────────────┘ └───────────────────┘
         │
┌────────▼──────────────────────────────── ┐
│  Security Layer (ECDH · AES-256-GCM)     │
└──────────────────────────────────────────┘
         │
┌────────▼──────────────────────────────── ┐
│  Transport (WifiP2pManager · NsdManager  │
│             ServerSocket · TCP)          │
└──────────────────────────────────────────┘
```

Full detail: [`Architecture.md`](./docs/Architecture.md)

---

## How Multi-Hop Routing Works

In Disaster Mode, devices that cannot reach each other directly relay through intermediate nodes:

```
[Device A]  ←——— out of range ———→  [Device D]
     |                                    |
     └——— [Device B] ——— [Device C] ———┘
              (relay)        (relay)

A's message to D travels:  A → B → C → D
```

HyperShare uses an **AODV-inspired on-demand routing protocol**:

1. **RREQ** — A broadcasts a Route Request when it has no route to D
2. **RREP** — D (or a node with a known route) sends a Route Reply back
3. **RERR** — If a relay goes down, affected nodes are notified and reroute
4. **HELLO beacons** — every 5 seconds; 3 missed = peer considered lost

Routes are cached in a `RoutingTable` with 30-second expiry and pruned continuously. Max hop count: 8.

Full detail: [`Mesh_Routing_Logic.md`](./docs/Mesh_Routing_Logic.md)

---

## Protocol

HyperShare uses a custom lightweight binary protocol — no JSON, no XML, no third-party framing library.

```
┌───────────────────────────────────── ──┐
│   ROUTING HEADER  (32 bytes, fixed)    │  ← visible to relay nodes
│   MAGIC · VERSION · TYPE · FLAGS · TTL │
│   SOURCE_ID · DEST_ID · SEQ · SESSION  │
├────────────────────────────────────────┤
│   ENCRYPTED PAYLOAD  (variable)        │  ← opaque to relay nodes
│   IV (12B) · AES-GCM ciphertext · TAG  │
└────────────────────────────────────────┘
```

Key design decisions:
- **FLAGS byte** has a dedicated `DISASTER_MODE` bit (bit 7) so routing logic can distinguish emergency traffic
- **Relay nodes** read only the routing header — payload is opaque to them
- **64 KB chunk size** for file transfer, calibrated for low-RAM Android devices
- **28-byte encryption overhead** per packet (12 IV + 16 GCM tag)

Full specification: [`Protocol_Specs.md`](./docs/Protocol_Specs.md)

---

## Project Documentation

| File | Contents |
|---|---|
| [`Architecture.md`](./docs/Architecture.md) | Android module breakdown, data flows, threading model, dependencies |
| [`Workflow.md`](./docs/Workflow.md) | Week-by-week 24-week development plan with milestones and risk register |
| [`Theme.md`](./docs/Theme.md) | UI design language — color palette, typography, glassmorphism spec, animation rules |
| [`Mesh_Routing_Logic.md`](./docs/Mesh_Routing_Logic.md) | Peer discovery, GO election scoring, AODV routing, route maintenance |
| [`Protocol_Specs.md`](./docs/Protocol_Specs.md) | Binary protocol — every packet type, header fields, byte offsets, encryption envelope |
| [`Testing_Scenarios.md`](./docs/Testing_Scenarios.md) | Physical and software test plans for both modes, performance benchmarks |
| [`Running_Steps.md`](./docs/Running_Steps.md) | Step-by-step device deployment, USB debugging, ADB pairing, and test execution |
| [`Bug_Log.md`](./docs/Bug_Log.md) | Production bug register, SLA severity classifications, and resolved issues |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Navigation |
| Async | Kotlin Coroutines + Flow |
| DI | Hilt (Dagger) |
| Persistence | Jetpack DataStore (Proto) |
| Crypto | Bouncy Castle (Curve25519 ECDH, AES-256-GCM) |
| Networking | `WifiP2pManager` (WiFi Direct), `NsdManager` (mDNS), raw `java.net.ServerSocket` |
| Logging | Timber (stripped in release) |

No third-party networking library. All socket management is hand-rolled to keep the binary protocol under full control.

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog or newer
- Android SDK 26+ (minSdk), target SDK 34
- Physical Android devices for testing — **emulators cannot simulate WiFi Direct**
- At least 3 physical devices for Mode 2 mesh tests; 4 for multi-hop tests

### Build & Run

```bash
git clone https://github.com/Pinuk14/hypershare.git
cd hypershare
./gradlew assembleDebug
```

Install on a physical device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### First Launch

1. Grant all requested permissions — **Nearby Devices (`NEARBY_WIFI_DEVICES`) on Android 13+ or Location (`ACCESS_FINE_LOCATION`) on Android 10–12** is mandatory for Wi-Fi Direct peer discovery and local networking.
2. The app defaults to Mode 1 (WiFi LAN). Connect all devices to the same WiFi network.
3. Peers appear automatically in the Peer List within 10 seconds.
4. To test Mode 2 (Disaster Mesh), tap **EMERGENCY MODE** on the Home screen to trigger Wi-Fi Direct scanning and group formation.

### Enabling ADB Logging

```bash
adb logcat -s HyperShare
```

The `RoutingDebugScreen` (debug builds only) shows the live routing table, hop counts, and TTL values for each known route.

---

## Testing

See [`Testing_Scenarios.md`](./Testing_Scenarios.md) for the full test plan. The most critical physical test:

**M2-TC-03 — Multi-Hop Distance Test:** Place Device A and Device D ~80 meters apart (out of direct WiFi Direct range), with Device B as a midpoint relay. Verify that a message sent from A arrives at D via B, and that the routing table on all three devices reflects the correct hop counts.

---

## Repository Structure

```
hypershare/
├── app/
│   └── src/main/java/com/hypershare/
│       ├── application/         # SessionManager, GroupManager, UserIdentityManager, TransferQueue
│       ├── db/                  # SQLite ChatDatabaseHelper, MessageRepository, GroupRepository
│       ├── identity/            # IdentityManager (Ed25519/BLAKE2b), ContactCard
│       ├── model/               # Packet, ConnectedPeer, GroupModels, GroupInviteCard, RouteEntry
│       ├── protocol/            # PacketBuilder, PacketParser, ChunkManager, ControlPacketHandler
│       ├── routing/             # RoutingTable, HopManager, GroupOwnerElection, ModeController
│       ├── security/            # EphemeralKeyPair (ECDH), SessionEncryptor (AES-GCM), PeerKeyStore, TofuManager
│       ├── service/             # MeshNetworkService, DiscoveryService, LanSocketManager, WifiDirectController
│       └── ui/                  # Jetpack Compose UI (chat, group, peerlist, home, settings, qrscanner)
├── docs/                        # Specifications, bug log, and technical documentation
│   ├── Architecture.md
│   ├── Bug_Log.md
│   ├── Mesh_Routing_Logic.md
│   ├── Protocol_Specs.md
│   ├── Running_Steps.md
│   ├── Testing_Scenarios.md
│   ├── Theme.md
│   └── Workflow.md
├── Filestructure.md             # AI Agent file structure index & change log
└── README.md                    # Main project overview
```

---

## Known Limitations (v1.0)

- **GO failover time** is 30–60 seconds — an inherent Android WiFi Direct limitation, not a bug in HyperShare.
- **Maximum group size** per WiFi Direct group is ~8 clients. Larger deployments require chained groups (experimental, documented in `Mesh_Routing_Logic.md`).
- **Mode 2 file transfer** is significantly slower than Mode 1 — WiFi Direct throughput varies widely across Android OEMs.
- **Dark mode only** in v1 — intentional for OLED battery efficiency in disaster scenarios.
- `NsdManager` on some OEMs stops discovering after ~30 minutes. HyperShare restarts discovery every 20 minutes as a workaround.

---

## License

MIT License — see [`LICENSE`](./LICENSE) for details.

---

<div align="center">

*Built for the times when everything else stops working.*

</div>
