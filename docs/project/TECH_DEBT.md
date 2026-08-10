# Technical debt register

| ID | Priority | Area | Debt | Exit condition |
|---|---:|---|---|---|
| SEC-001 | P0 | Android | Provider secrets are represented in the Room service-instance model | Secrets migrated to Keystore-backed store; database contains only references |
| SEC-002 | P0 | Android | Global cleartext traffic is enabled | Cleartext disabled globally; per-instance compatibility is explicit and scoped |
| SEC-003 | P0 | iOS | ATS allows arbitrary loads broadly | Global arbitrary loads removed; explicit trust policy implemented |
| SEC-004 | P0 | Both | Self-signed compatibility can bypass normal trust semantics | Four explicit TLS modes with warnings, migration and tests |
| ARCH-001 | P1 | Android | Central authentication/network routing has provider-specific branching | Provider auth strategies implement a stable interface |
| ARCH-002 | P1 | Both | Global views consume service-specific models | Normalized capability/resource/event contracts adopted incrementally |
| OPS-001 | P1 | CI | No SBOM, provenance or automated signed release | Reproducible artifacts, SBOM and attestations published |
| OPS-002 | P1 | MSP | Tenant and customer scoping is not a universal invariant | Tenant isolation tests pass for every gateway query/action |

Owners and target milestones are assigned when the matching Phase-1 issues are created.
