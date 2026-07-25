# HyperShare — UI Theme & Design Language

> Version 0.1 — Initial direction: Dark Glassmorphism with Signal-State UI  
> This document is intended to be modified. Mark any revised section with `[rev: your-name, date]`.

---

## 1. Design Philosophy

HyperShare is infrastructure-critical software — it is used when other tools have failed. The visual language should communicate **clarity, trust, and operational status** above all else. It should also feel like a modern consumer app, not a military radio utility.

The design borrows from:
- **Glassmorphism** — frosted card surfaces that suggest layering and depth without heavy drop shadows
- **Signal/Status UI** — aviation-inspired status indicators (color = state, not decoration)
- **OLED-first dark mode** — deep black backgrounds (#0A0A0F) that extend battery life on OLED displays, which are common on the disaster-use-case devices (budget Android phones often have AMOLED screens)

**One visual risk taken:** The mesh network topology is visualized as a live, ambient node graph on the peer list background — a subtle, slowly-animated graph of current nodes. This is functional (shows who's connected to whom) and distinctive (no other messaging app does this).

---

## 2. Color Palette

```
┌──────────────────────────────────────────────────────────────┐
│  SURFACE COLORS                                              │
│                                                              │
│  Background Base      #0A0A0F   ██  Deep space black         │
│  Surface Card         #16161F   ██  Slightly lifted surface  │
│  Glass Overlay        rgba(255,255,255,0.05)  Frost layer   │
│  Glass Border         rgba(255,255,255,0.08)  Rim light     │
│  Surface Elevated     #1E1E2D   ██  Modals, sheets          │
│                                                              │
│  ACCENT COLORS                                               │
│                                                              │
│  Signal Blue          #3B82F6   ██  Primary CTA, links      │
│  Signal Blue Dim      #1D4ED8   ██  Pressed state           │
│  Mesh Teal            #14B8A6   ██  Mode 2 / Disaster mode  │
│  Mesh Teal Glow       rgba(20,184,166,0.15)  Status halo    │
│                                                              │
│  STATE COLORS                                               │
│                                                              │
│  Connected Green      #22C55E   ██  Peer connected          │
│  Warning Amber        #F59E0B   ██  Weak signal, retrying   │
│  Error Red            #EF4444   ██  Peer lost, error state  │
│  Relay Purple         #A855F7   ██  Relaying for other node │
│  Offline Gray         #374151   ██  Peer in list, unreached │
│                                                              │
│  TEXT COLORS                                               │
│                                                              │
│  Text Primary         #F1F5F9   ██  Main content            │
│  Text Secondary       #94A3B8   ██  Metadata, timestamps    │
│  Text Disabled        #475569   ██  Inactive / grayed       │
└──────────────────────────────────────────────────────────────┘
```

**Important signal convention:**
- `Signal Blue` (#3B82F6) = Mode 1 (WiFi LAN). Normal operation.
- `Mesh Teal` (#14B8A6) = Mode 2 (Disaster/Mesh). Emergency operation.
- This distinction is used consistently across status chips, icons, and the ambient background graph.

---

## 3. Typography

| Role | Typeface | Weight | Size | Usage |
|---|---|---|---|---|
| Display | **Inter** | 700 Bold | 28sp | Screen titles, peer names (large) |
| Body | **Inter** | 400 Regular | 14sp | Message text, file names |
| Caption | **JetBrains Mono** | 400 Regular | 11sp | Packet IDs, routing debug, IP addresses, timestamps |
| Status | **Inter** | 600 SemiBold | 12sp | Status chips ("CONNECTED", "RELAYING") |
| Button | **Inter** | 600 SemiBold | 14sp | All CTA buttons |

**Why JetBrains Mono for captions?** Technical metadata (IPs, peer IDs, hop counts) reads more clearly in a monospace face and signals to the user that this is precise data. It also gives HyperShare a subtle "system terminal" credibility that fits the disaster-communication context.

Both fonts are available in the Google Fonts Compose library and add no build complexity.

---

## 4. Glass Card System

The core UI primitive is the **Glass Card** — a frosted, bordered surface for all interactive panels.

```
Glass Card Spec:
  Background:     rgba(255, 255, 255, 0.05)
  Border:         1dp solid rgba(255, 255, 255, 0.08)
  Corner radius:  16dp (content cards), 12dp (list items), 8dp (chips)
  Blur:           BackdropBlur(8dp) — Compose modifier, applied where supported
  Elevation:      0dp (no shadow — the glass layer implies depth)
```

**Compose implementation:**
```kotlin
fun Modifier.glassCard(cornerRadius: Dp = 16.dp): Modifier = this
    .background(
        color = Color(0x0DFFFFFF),  // 5% white
        shape = RoundedCornerShape(cornerRadius)
    )
    .border(
        width = 1.dp,
        color = Color(0x14FFFFFF),  // 8% white
        shape = RoundedCornerShape(cornerRadius)
    )
```

---

## 5. Status Chip System

Status chips are small, pill-shaped labels that appear next to peer names. They communicate operational state without requiring the user to read prose.

| State | Label | Color | Behavior |
|---|---|---|---|
| Mode 1 Active | `WIFI` | Signal Blue bg, white text | Solid |
| Mode 2 Active | `MESH` | Mesh Teal bg, white text | Pulsing glow animation |
| Peer Connected | `CONNECTED` | Connected Green | Static |
| Peer Relaying | `RELAY · 2 HOP` | Relay Purple | Static, hop count updates live |
| Peer Pending | `CONNECTING…` | Warning Amber | Shimmer animation |
| Peer Lost | `LOST` | Error Red | Fades out after 8s |

**Disaster Mode chip animation:** When the app is in Mode 2, the `MESH` chip has a repeating glow pulse (scale 1.0→1.04, alpha 1.0→0.7, 1.5s loop). This is the only looping animation in the app — it signals that disaster mode is active without being alarming.

---

## 6. Screen-by-Screen Layout Notes

### `PeerListScreen`
- Background: ambient node graph (slow-moving dots connected by lines, representing actual routing topology). Rendered on a `Canvas` composable behind the list.
- Peer items: `GlassCard` list items, 72dp tall. Left: avatar initials circle with state-color border. Right: `Status chip` stack.
- FAB (top-right): Mode toggle button. Blue WiFi icon (Mode 1) ↔ Teal mesh icon (Mode 2).

### `ChatScreen`
- Message bubbles: outgoing = `Signal Blue` (16dp radius, flat bottom-right). Incoming = `GlassCard` (16dp radius, flat bottom-left).
- Delivery status: below outgoing bubble — single gray tick (sent) → double gray tick (relayed) → double teal tick (received). Explicitly shows if message was relayed through intermediate nodes.
- Header: peer name + `Status chip` + hop count if > 1 (`"via 2 hops"` in `Text Secondary` color).

### `FileBrowserScreen`
- File list: `GlassCard` rows. File type icon (left), filename + size (center), permission toggle (right).
- Permission toggle: `Switch` with label `VIEW ONLY ↔ DOWNLOAD`. Not a checkbox — a proper toggle because the distinction is important.
- Transfer progress: linear `ProgressIndicator` in `Signal Blue` replaces the file row during transfer.

### `ModeToggleScreen`
- Full-screen modal sheet (not a separate screen) with two large cards:
  - Card 1: WiFi icon, `Mode 1 — Local Network`, description
  - Card 2: Mesh icon, `Mode 2 — Disaster Mesh`, description, `⚠ Only use when infrastructure has failed` warning
- Active mode has a `Connected Green` border. Inactive is `GlassCard` default.

### `RoutingDebugScreen` (dev/debug build only)
- Full-screen `Canvas` with routing table rendered as a force-directed graph.
- Node colors match peer state colors above.
- Tap a node: shows `RouteEntry` details in a bottom sheet.

---

## 7. Motion & Animation Principles

**Rule:** Animate state changes, not decorations.

| Animation | Duration | Easing | Purpose |
|---|---|---|---|
| Screen enter/exit | 300ms | FastOutSlowIn | Navigation transitions |
| Status chip state change | 200ms crossfade | LinearOutSlowIn | Peer state update |
| Message received | 150ms scale + fade in (0.8→1.0) | OvershootInterpolator(1.2) | New message feel |
| MESH chip pulse | 1500ms loop | EaseInOut | Disaster mode active signal |
| Ambient graph drift | 4000ms per cycle | Linear | Background topology visualization |
| Transfer progress | Real-time, no animation delay | — | Accuracy over smoothness |

No `AnimatedVisibility` with default cross-fade on performance-critical paths (transfer list rows). Use explicit alpha animations with `animateFloatAsState` instead.

---

## 8. Iconography

Use **Material Symbols Rounded** (variable font). Never mix Rounded with Sharp or Outlined variants in the same screen.

| Concept | Icon Name |
|---|---|
| Mode 1 — WiFi | `wifi` |
| Mode 2 — Mesh | `hub` |
| Send message | `send` |
| File transfer | `upload_file` |
| Download (permitted) | `download` |
| View only (locked) | `visibility` |
| Peer connected | `person` |
| Relay node | `swap_horiz` |
| Hop count | `route` |
| Settings | `settings` |
| Disaster alert | `warning` |

---

## 9. Spacing & Grid

- Base unit: **4dp**
- Screen horizontal padding: **16dp**
- Card internal padding: **16dp** (top/bottom), **12dp** (left/right)
- List item spacing: **8dp** gap between items
- Section title to first item: **12dp**
- Bottom navigation height: **80dp** (includes safe area inset)

---

## 10. Dark Mode Only

HyperShare ships **dark mode only** in v1. Reasons:
1. Battery efficiency on AMOLED devices (disaster use = low battery scenarios).
2. Usability in low-light disaster environments (tents, rubble, nighttime).
3. Design coherence — glassmorphism is far more effective on dark backgrounds.

Light mode may be added in a future version. Do not add light mode color tokens now; it invites inconsistency.

---

## 11. Modification Log

> Record changes here when revising the theme. This document is version-controlled but the log helps with quick cross-referencing.

| Date | Section | Change | By |
|---|---|---|---|
| — | — | Initial draft | Pinak |
