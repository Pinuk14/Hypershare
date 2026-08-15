# HyperShare — Bug Log & Production Issues Register

> **Maintenance Guideline**: Record all production bugs, edge-case failures, hardware/OEM quirks, and runtime exceptions encountered during development and testing here.  
> Update status and resolution as issues are triaged and fixed.

---

## 1. Issue Severity Standards

| Severity | Definition | SLA / Target Resolution |
|---|---|---|
| **P0 — Critical** | App crash, security vulnerability, mesh network deadlock, data corruption | Immediate block / Fix within 24h |
| **P1 — High** | Mode transition failure, chunk ACK timeout, socket leak, battery drain | Fix before phase release |
| **P2 — Medium** | UI glitch, delayed peer discovery (>5s), formatting error | Fix during weekly polish |
| **P3 — Low** | Minor cosmetic issue, non-critical log warning | Backlog |

---

## 2. Issue Register

### [ISSUE-004] QR Code Share ID Scan & Security Key Exchange Workflow
- **Severity**: P1 — High
- **Phase/Week**: Phase 1 — Week 5 & Phase 0 Integration
- **Component**: `ui/components/QrCodeCard`, `security/TofuManager`, `security/PeerKeyStore`
- **Environment**: All Android devices with camera support
- **Description**: Scanning a peer's QR Share ID on the Scan screen must extract the peer's public key fingerprint and device metadata, perform ECDH shared key derivation, verify TOFU (Trust-On-First-Use) key store pinning, and authorize encrypted peer-to-peer communication.
- **Root Cause**: Planned security feature to be fully wired with CameraX / QR scanner integration during Phase 1 peer discovery and key exchange.
- **Status**: IN_PROGRESS (Logged for Phase 1 security wiring)

---

### [ISSUE-005] Group QR Code Payload Regenerates Every 1 Second
- **Severity**: P1 — High
- **Phase/Week**: Phase 1 — Week 8
- **Component**: `ui/group/GroupChatScreen.kt` (`QrInviteFullScreenModal`)
- **Environment**: All Android devices
- **Description**: The group invite QR code matrix re-renders and changes every second because `remember(groupId, remainingSeconds)` depends on the 1-second countdown timer. This makes camera scanning virtually impossible.
- **Root Cause**: `remember(groupId, remainingSeconds)` invalidates every second as `remainingSeconds` counts down, regenerating `GroupInviteCard` timestamp and signature payload.
- **Resolution**: Keyed `qrPayload` `remember` block strictly on `groupId` instead of `remainingSeconds`. The QR payload remains static for the 2-minute modal lifetime while the UI countdown badge continues updating.
- **Status**: RESOLVED

---

### [ISSUE-006] Broadcast Invite TCP Packets Undelivered to GroupManager
- **Severity**: P0 — Critical
- **Phase/Week**: Phase 1 — Week 8
- **Component**: `service/LanSocketManager.kt`, `ui/group/GroupChatViewModel.kt`
- **Environment**: All Android devices
- **Description**: Incoming group packets (`0x20`–`0x28`) are ignored by `LanSocketManager` because the packet type switch statement hits `else -> {}`. Broadcast invite notifications are never received or posted.
- **Root Cause**: `LanSocketManager.kt` lacks delegation to `GroupManager.handleGroupPacket()` for group packet types `GROUP_CREATE` through `GROUP_RESTORE`.
- **Resolution**: Added delegation in `LanSocketManager.kt` for packet types `0x20`–`0x28` to `groupManager.handleGroupPacket()`. Updated `GroupChatViewModel.kt`'s `broadcastInvite` to send `GROUP_CREATE` packets via `groupManager.broadcastGroupInvite()`.
- **Status**: RESOLVED

---

### [ISSUE-007] Missing Long-Press Option to Delete / Dissolve Group Chat
- **Severity**: P2 — Medium
- **Phase/Week**: Phase 1 — Week 8
- **Component**: `ui/peerlist/PeerListScreen.kt` (`GroupGlassListItem`)
- **Environment**: All Android devices
- **Description**: Long-pressing a group item on the peer list screen does not show a deletion dialog to remove or dissolve the group chat thread.
- **Root Cause**: `GroupGlassListItem` only implemented `clickable`, lacking `combinedClickable(onLongClick = ...)` and confirmation dialog logic.
- **Resolution**: Updated `GroupGlassListItem` to use `combinedClickable` with `onLongClick` handler. Added a confirmation `AlertDialog` ("Delete Group Chat?") in `PeerListScreen.kt` calling `GroupRepository.deleteGroup()`.
- **Status**: RESOLVED

---

## 3. Bug Report Template (Copy for new issues)

```markdown
### [ISSUE-XXX] Short Title
- **Severity**: P0 / P1 / P2 / P3
- **Phase/Week**: Phase X — Week Y
- **Component**: `layer/ComponentName`
- **Environment**: Device Model, Android OS Version, API Level
- **Description**: Detailed description of what occurred vs expected behavior.
- **Steps to Reproduce**:
  1. Step 1
  2. Step 2
  3. Step 3
- **Root Cause**: Technical analysis of why the bug occurred.
- **Resolution**: Description of the fix, PR link, or mitigation strategy.
- **Status**: [OPEN / IN_PROGRESS / RESOLVED / WONT_FIX]
```
