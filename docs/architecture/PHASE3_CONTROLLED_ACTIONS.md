# Phase 3 controlled actions

Phase 3 establishes one policy, durable execution and audit contract in both native clients before additional provider mutations are exposed through the global operations workspace.

## Invariants

1. Every mutation is represented by a typed request with provider, action, target, risk, request time and an idempotency key.
2. Action names use a bounded normalized identifier and never contain arbitrary scripts or shell commands.
3. A provider must declare write capability before policy evaluation can approve execution.
4. Viewer, operator and administrator roles are evaluated locally. Gateway-backed deployments must still enforce authorization server-side.
5. Medium and higher risk actions require explicit confirmation. High and critical actions require an administrator role.
6. Dry-run requests evaluate validation, capability and role policy without invoking the provider mutation.
7. The coordinator serializes provider mutations across suspension points and coalesces concurrent requests with the same idempotency key.
8. Audit records are append-only value objects. They contain normalized identifiers and reason codes, but no parameters, credentials, headers or response bodies.
9. Failed operations record an error type or bounded reason code rather than an exception message that could contain provider data.
10. Arbitrary shell and script execution remain excluded by ADR-0008.
11. An approved request is persisted before its provider mutation starts. Terminal results are retained with the idempotency key, so a coordinator restart does not replay a completed mutation.
12. Durable entries persist normalized identity only. Action parameters are stripped before storage; credentials, request headers and provider response bodies are never queued.
13. Only explicitly retryable transport failures for low- and medium-risk actions are retried automatically. The default policy permits three attempts with capped exponential backoff.
14. High- and critical-risk failures never retry automatically. A retryable failure at those risk levels requires manual review.
15. An action found in `executing` state after process restart has an indeterminate provider outcome and transitions to `manual_review`. It is never replayed automatically.
16. A persistence failure after a successful provider call transitions the in-memory result to `manual_review`; it never re-enters the provider retry path.

## Durable state model

```text
queued -> executing -> succeeded
             |
             +-> retry_wait -> executing       (low/medium, bounded)
             |
             +-> failed                         (non-retryable)
             |
             +-> manual_review                  (high/critical or retry exhausted)

process restart while executing -> manual_review
```

Android stores the bounded queue in the existing Preferences DataStore. iOS uses a bounded Codable payload in UserDefaults. Both implementations retain at most 500 entries and fail before mutation when persistence cannot complete. The in-memory store remains available for deterministic unit tests.

Recovery is deliberately conservative. Queued or retry-wait entries can only continue when the caller rebinds the typed provider operation. Indeterminate entries require an operator decision; there is no background replay of a closure or reconstructed arbitrary payload. This does not claim distributed exactly-once execution: gateway deployments should add server-side idempotency and provider task reconciliation.

## Provider scope

Proxmox VE is the reference-provider migration. Guest start and resume are low risk, graceful shutdown, reboot and suspend are medium risk, and hard stop is high risk. Both clients route these lifecycle mutations through the coordinator. Android requests explicit confirmation for medium and high risk actions; the existing iOS confirmation dialog marks the same requests as confirmed. Provider task tracking remains unchanged after an approved mutation.

Portainer is the first risk-class migration beyond the reference provider. Container start is low risk; stop, restart, pause and resume are medium risk; kill and removal are high risk. Android list/detail lifecycle actions and iOS list/detail lifecycle actions use the same coordinator, provider capability gate and normalized target identity. iOS container removal is also controlled. Medium and high risk operations require an explicit confirmation dialog before the provider call. Compose edits, rename and other configuration mutations remain outside this vertical and must be migrated separately.

Healthchecks is the first monitoring-provider migration. Check editing, integration-channel updates, pause and resume are medium risk, while creation and deletion are high risk. Android and iOS editor/detail actions use normalized Healthchecks instance and check UUID target identities (check/new for creation), explicit Save or confirmation actions, the write-capability gate and the shared local audit trail. Creation transport failures are never retried automatically because the provider offers no idempotency token; they enter manual review to prevent duplicate checks. Payload fields remain provider-local and are not persisted in the action audit or durable queue.

Direct self-hosted mobile clients currently evaluate these existing privileged buttons as the administrator role to preserve the pre-Phase-3 operating model. This is not a trust boundary: gateway-backed deployments must derive the actor role server-side and reject unauthorized requests independently.

The bounded mobile ledger and queue are operational state, not a compliance archive. Phase 5 gateway deployments can export signed or integrity-protected audit events to durable self-hosted storage.
