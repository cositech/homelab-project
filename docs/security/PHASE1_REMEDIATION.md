# Phase-1 security remediation

Date: 2026-08-10. Scope: SEC-001 through SEC-004 from the Phase-0 security audit and the secure provider-capability foundation.

## Security invariants

1. Provider secrets are stored separately from service-instance metadata.
2. Metadata carries only an opaque `credentialRef`; missing or unreadable envelopes do not silently produce usable credentials.
3. Legacy credentials are removed only after the replacement envelope has been written and read back successfully.
4. System certificate validation is the default on both platforms.
5. Custom trust anchors, certificate pins, and verification bypass are distinct instance-scoped policies.
6. Global Android cleartext and broad iOS ATS exemptions remain disabled.
7. Provider requests reject non-HTTPS URLs before credentials are attached to headers or bodies.
8. Provider endpoint construction validates the HTTPS authority before combining paths, rejects embedded URL credentials, and redacts secret-bearing query parameters from request logs.

## Finding disposition

### SEC-001 — Android credentials in Room

**Remediated.** `ServiceInstanceEntity` contains metadata and `credentialRef`, but no provider token, password, cookie, API key, client secret, OTP, or custom CA. `AndroidKeystoreCredentialStore` encrypts serialized credential envelopes with an Android Keystore AES-256/GCM key. Room migration 6-to-7 writes and verifies every envelope before replacing the legacy table. Repository save, load, delete, and legacy DataStore import use the same abstraction.

### SEC-002 — Android global cleartext

**Remediated.** The application manifest and network-security configuration deny cleartext globally. New connections default to system TLS validation. Compatibility bypass remains explicit and instance-scoped.

### SEC-003 — Broad iOS ATS exemptions

**Remediated.** General, media, and web-content arbitrary-load exemptions were removed. Local-network access remains declared because direct self-hosted provider access is a product requirement; provider requests still require HTTPS and trust behavior is controlled per instance.

### SEC-004 — Self-signed trust compatibility

**Remediated at the transport and persistence boundary.** Android and iOS model `SYSTEM`, `CUSTOM_CA`, `CERTIFICATE_PIN`, and `INSECURE_COMPATIBILITY` separately. System trust is the default. Custom anchors and SHA-256 certificate pins retain normal certificate validation semantics. The compatibility bypass is preserved only for legacy/self-hosted recovery and is never selected implicitly.

### CodeQL — Sensitive data in provider URLs

**Remediated.** The iOS network engine no longer concatenates an unvalidated base URL and a potentially secret-bearing path. It parses and validates an HTTPS base authority first, rejects embedded authority credentials, then combines relative path and query components while reasserting the literal HTTPS scheme. Legacy APIs that require a query token remain functional over HTTPS, but auth, token, password, API-key, secret, and session query values are redacted before request URLs reach application logs.

## Provider core

Both native clients now expose a registry for every upstream service type, typed capability discovery, and normalized health, resource, and event models. Proxmox VE and Uptime Kuma provide the reference descriptors and health adapters while retaining their existing client behavior.

## Verification

- `bash ./scripts/phase1-security-audit.sh`
- Android debug Kotlin compilation
- Android debug unit tests
- iOS unsigned device compilation
- iOS simulator unit tests
- CodeQL for Kotlin/Java and Swift
- dependency review and Phase-0 foundation audit

The delivery pull request must keep all of these gates green before it is moved out of draft state or merged.

## Residual risks and follow-up

- `INSECURE_COMPATIBILITY` intentionally remains available for legacy private infrastructure. Phase 3 must attach normalized action/audit events before privileged cross-provider actions are introduced.
- Device compromise can expose secrets while the application is unlocked; the stores reduce extraction and backup exposure but do not protect a fully compromised runtime.
- Encrypted backups still intentionally contain credentials and retain the Phase-0 backup-hardening follow-up.
