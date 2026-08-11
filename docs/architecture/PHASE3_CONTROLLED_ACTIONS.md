# Phase 3 controlled actions

The first Phase-3 delivery establishes the same policy and audit contract in both native clients before any additional provider mutation is exposed through the global operations workspace.

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

## Scope

This foundation includes policy, serialized execution, dry-run behavior, idempotency and bounded in-memory audit history for Android and iOS. Terminal idempotency results are retained separately for the coordinator lifetime, so audit pruning cannot cause a repeated mutation.

Proxmox VE is the reference-provider migration. Guest start and resume are low risk, graceful shutdown, reboot and suspend are medium risk, and hard stop is high risk. Both clients now route these lifecycle mutations through the coordinator. Android requests explicit confirmation for medium and high risk actions; the existing iOS confirmation dialog marks the same requests as confirmed. Provider task tracking remains unchanged after an approved mutation.

Direct self-hosted mobile clients currently evaluate these existing privileged buttons as the administrator role to preserve the pre-Phase-3 operating model. This is not a trust boundary: gateway-backed deployments must derive the actor role server-side and reject unauthorized requests independently.

The bounded mobile ledger is an operational history, not a compliance archive. Phase 5 gateway deployments can export signed or integrity-protected audit events to durable self-hosted storage.
