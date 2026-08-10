# Threat model

## Assets

Provider credentials, sessions, customer/tenant mappings, inventory, monitoring and ticket data, action authority, audit records, encrypted backups, signing identities and update metadata.

## Trust boundaries

1. Mobile UI to local secure storage.
2. Mobile app to provider API across LAN, VPN or Internet.
3. Mobile app to optional gateway.
4. Gateway to tenant/provider networks and secret store.
5. CI to GitHub, dependencies, signing and release artifacts.

## Principal threats and controls

| Threat | Primary controls |
|---|---|
| Device/database extraction | Keychain/Keystore, secret references, biometric/device protection |
| MITM or malicious local network | secure defaults, custom CA/pins, no global bypass |
| Cross-tenant data/action access | server-side tenant scope, entity authorization, negative tests |
| Confused-deputy action | typed scopes, risk policy, confirmation, idempotency, audit |
| Secret leakage in logs/backups/URLs | centralized redaction, authenticated encryption, no query secrets |
| Compromised dependency/action | dependency review, CodeQL, Dependabot, later immutable pins/SBOM/provenance |
| Malicious or compromised provider | schema validation, response limits, timeouts, least-privilege tokens |
| Stale or replayed operations | expiry, nonce/idempotency key, current-state validation |

## Non-goals for Phase 0

Phase 0 does not claim runtime remediation or external penetration testing. It establishes the controls, evidence and exit gates for implementation.
