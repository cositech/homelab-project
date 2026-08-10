# Technical Debt Register

Priority scale: P0 blocks production-grade fork foundation; P1 should be solved
early; P2 can be incremental.

| ID | Priority | Area | Finding | Target |
|---|---:|---|---|---|
| TD-001 | P0 | Android secrets | Credentials in Room entity/database | SecureCredentialStore + migration |
| TD-002 | P0 | TLS Android | Global cleartext allowed | Per-instance explicit policy |
| TD-003 | P0 | TLS Android | Trust-all client/hostname verifier | custom CA/pinning model |
| TD-004 | P0 | TLS iOS | ATS arbitrary loads | narrow exceptions / explicit compatibility |
| TD-005 | P0 | CI | Unit tests not executed | test gates |
| TD-006 | P0 | Architecture | Provider behavior coupled to service-specific layers | capability/provider contracts |
| TD-007 | P1 | Android network | Large central AuthInterceptor | auth strategies per provider |
| TD-008 | P1 | Logging | Debug body logging | structured redacted logging |
| TD-009 | P1 | Cross-platform | Feature parity not guaranteed | integration parity matrix |
| TD-010 | P1 | Dependency mgmt | No update-bot config | automated PRs |
| TD-011 | P1 | Security CI | No CodeQL/dependency review | security workflows |
| TD-012 | P1 | Testing | Limited provider fixture coverage | contract fixtures |
| TD-013 | P1 | Identity | Service-local IDs only | normalized ResourceIdentity |
| TD-014 | P1 | Multi-tenancy | Multi-instance != org/site tenancy | org/site model |
| TD-015 | P1 | Actions | No normalized risk/policy model | ActionCapability + policy |
| TD-016 | P1 | Search | Per-service navigation | universal resource search |
| TD-017 | P1 | Observability | No normalized health/incident model | common domain |
| TD-018 | P2 | Naming | Legacy package/product naming | controlled rebrand |
| TD-019 | P2 | Build | Ruby xcodeproj install not locked | Gemfile.lock |
| TD-020 | P2 | Release | no fork release governance yet | signed/reproducible releases |
| TD-021 | P2 | Architecture | duplicated Kotlin/Swift domain logic | evaluate KMP only after Phase 2 |
| TD-022 | P2 | AI | no deterministic AI boundary | MCP/AIOC separate plane |
