# Phase-0 security audit

Date: 2026-08-10. Scope: static review of the current fork baseline; no live endpoints or production credentials were used.

## Critical migration requirements

### SEC-001 — Android credentials in Room (High)

The Android service-instance persistence model carries authentication material such as tokens, API keys, passwords, cookies and client secrets. A database extraction, debug backup, rooted device, or accidental logging path can therefore expose every configured provider.

**Required:** introduce a Keystore-backed `SecureCredentialStore`, store only an opaque `credentialRef` in Room, migrate transactionally, verify before deleting legacy fields, redact diagnostics, and cover upgrade/rollback with tests.

### SEC-002 — Android global cleartext (High)

`AndroidManifest.xml` sets `usesCleartextTraffic="true"` and the base network security configuration permits cleartext globally. Any provider can therefore be configured over HTTP without a provider-specific policy boundary.

**Required:** secure global default, explicit per-instance compatibility policy, visible warning, and prohibition for high-risk background actions.

### SEC-003 — Broad iOS ATS exemptions (High)

`Info.plist` enables arbitrary loads generally, for media, and web content. This makes secure transport dependent on individual call-site behavior.

**Required:** remove global exemptions and use explicit trust handling for local/self-hosted systems.

### SEC-004 — Self-signed trust compatibility (High)

Both platforms expose `allowSelfSigned` behavior. A boolean cannot distinguish custom trust anchors, a deliberate certificate pin, and a verification bypass.

**Required TLS modes:** `SYSTEM`, `CUSTOM_CA`, `CERTIFICATE_PIN`, `INSECURE_COMPATIBILITY`. The last mode is temporary, instance-scoped, warned, audited, and never silently inherited.

## Medium findings

- Backups intentionally contain credentials; compatibility, KDF parameters, authenticated encryption, resource limits and wrong-password behavior require regression tests.
- Provider clients retain credentials in memory. Lifetimes should be minimized and values excluded from descriptions, errors and telemetry.
- High-impact Proxmox/container/network operations need normalized risk classes and immutable audit results before global actions are introduced.
- Future gateway/MSP APIs must enforce tenant and entity scope server-side; client filters are not authorization.
- API tokens embedded in artwork URLs can leak via caches, proxy logs and referrers and should be replaced with authenticated fetches.

## Phase-0 disposition

No runtime security behavior is changed in Phase 0 to avoid an untested credential or connectivity migration. SEC-001 through SEC-004 block new provider expansion and are the first Phase-1 delivery slice.
