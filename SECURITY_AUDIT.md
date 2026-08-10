# Phase 0 Security Audit

## Executive assessment

The upstream application is suitable as a functional self-hosted client
baseline, but its security model should be strengthened before it is used as a
multi-customer operational control plane.

No claim is made that the findings below are exploitable vulnerabilities.
They are architecture/risk findings from a static review.

## P0 findings

### S-001 — Android credentials persisted in ordinary Room database

**Observed**

`ServiceInstanceEntity` includes credential/token-like fields including:

- `authHeader`
- `accessToken`
- `username`
- `password`
- `apiKey`
- `secret`
- `apiTokenId`
- `apiTokenSecret`
- `sessionCookie`
- `refreshToken`
- `idToken`
- `clientSecret`
- custom headers

The Room database is built as `homelab.db` without database encryption in the
reviewed database module.

**Risk**

Application sandboxing is valuable but not equivalent to hardware-backed
secret storage. A rooted/compromised device, forensic extraction, debug
instrumentation or future backup/configuration mistakes can expose credentials.

**Target**

- remove secrets from `ServiceInstanceEntity`;
- store only a `credentialRef`;
- keep secret payload in Android Keystore-backed storage;
- use Android Keystore encryption keys, optionally hardware-backed/StrongBox;
- support migration with integrity checks;
- wipe old DB columns after verified migration;
- prevent secret material in state serialization and analytics.

**Compatibility rule**

Do not break existing installations. Migration must be transactional and tested
against representative legacy database fixtures.

---

### S-002 — Globally permitted Android cleartext traffic

**Observed**

Application manifest/network security configuration permits cleartext traffic
globally.

**Risk**

Credentials may be transmitted over HTTP when a service is configured without
TLS.

**Target**

- secure-by-default global policy;
- explicit per-instance "allow HTTP" compatibility opt-in;
- visual warning and risk metadata;
- disallow HTTP in gateway/enterprise profile;
- add migration UI for existing HTTP instances.

---

### S-003 — Optional Android trust-all TLS client

**Observed**

An alternate OkHttp client can use a permissive SSL socket factory and a
hostname verifier returning true. It is selectable for service instances to
support self-signed installations.

**Risk**

Trust-all TLS does not authenticate the endpoint and is vulnerable to MITM on
an untrusted path.

**Target**

Replace trust-all with explicit trust modes:

```text
SYSTEM
CUSTOM_CA
CERTIFICATE_PIN
INSECURE_COMPATIBILITY
```

`INSECURE_COMPATIBILITY`:

- cannot be default for new enterprise-mode instances;
- must display a persistent warning;
- must never silently activate;
- should be removable after a migration period.

---

### S-004 — iOS ATS allows arbitrary loads

**Observed**

`Info.plist` enables:

- `NSAllowsArbitraryLoads`
- `NSAllowsArbitraryLoadsForMedia`
- `NSAllowsArbitraryLoadsInWebContent`
- `NSAllowsLocalNetworking`

**Risk**

The broad ATS bypass weakens platform transport protections.

**Target**

Keep local-network support but remove broad arbitrary-load exceptions where
possible. Implement explicit compatibility controls analogous to Android.

## P1 findings

### S-005 — Debug HTTP body logging can disclose secrets/PII

**Observed**

The Android debug logging interceptor redacts selected header names but may
log textual request/response bodies in full.

**Risk**

API responses can contain:

- tokens;
- user records;
- tickets;
- customer information;
- server configuration;
- cookies embedded in JSON;
- other operational secrets.

**Target**

- body logging off by default;
- structured metadata logging;
- allowlisted diagnostic fields;
- maximum body length;
- key-based JSON redaction;
- explicit developer setting;
- never log token/secret values.

---

### S-006 — iOS stores full service state in Keychain

**Observed**

iOS persists encoded `ServiceStateV2` through the Keychain using
`kSecAttrAccessibleWhenUnlocked`.

**Assessment**

This is materially stronger than ordinary database storage. The architecture
should nevertheless split configuration metadata from secret material so both
platforms share the same model.

**Target**

- ordinary non-secret instance config in app storage;
- secret payload in Keychain;
- `credentialRef` from config to Keychain item;
- optional access-control flags for sensitive credentials.

## Security target state

```text
ServiceInstance
├── identity/configuration
├── endpoint
├── TLS policy
├── credentialRef ─────────────┐
└── capability settings        │
                               ▼
                        SecureCredentialStore
                        ├── Android Keystore
                        └── iOS Keychain
```

## CI security gates

Phase 0 package adds/recommends:

- CodeQL for Kotlin and Swift;
- dependency review on pull requests;
- Gradle wrapper validation;
- secret scanning where GitHub repository settings permit it;
- no credential fixtures;
- sanitized API fixtures only.

## Required security tests

Before Phase 1 is considered stable:

- migration from legacy Android DB preserves credentials;
- migrated DB contains no legacy secret values;
- secure storage read/write/delete tests;
- HTTP policy tests;
- self-signed custom-CA tests;
- certificate mismatch rejection;
- hostname mismatch rejection;
- debug logger redaction tests;
- backup/export encryption tests;
- biometric/step-up action tests;
- action audit completeness tests.
