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
