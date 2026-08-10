# Roadmap

## Phase 0 — Fork foundation

**State:** package prepared.

Exit gates:

- fork relationship/history preserved;
- working branch created;
- CI replacement green;
- tests execute on both platforms;
- upstream integrations catalogued;
- security baseline accepted;
- provider/capability architecture accepted;
- Phase 1 issues created.

## Phase 1 — Core architecture

### 1.1 Domain foundation

- Organization/Site/IntegrationInstance/Resource
- ResourceIdentity + relations/tags
- Health/Alert/Incident models
- capability registry
- provider metadata/spec parser
- universal search foundation

### 1.2 Security foundation

- Android credential migration to secure storage
- iOS secret/config split
- TLS trust modes
- debug logging hardening
- action risk model
- security migration UX

### 1.3 Reference provider migrations

- Proxmox VE
- Uptime Kuma
- Gitea/Forgejo

These must prove backwards compatibility.

## Phase 2 — COStech core operations

New priority providers:

1. Proxmox Backup Server
2. NetBox
3. Prometheus
4. Grafana
5. Zammad
6. PegaProx
7. OneUptime
8. OPNsense
9. WireGuard

Deliverables:

- correlated asset view;
- unified health;
- support/ticket linkage;
- monitoring events;
- initial backup center.

## Phase 3 — Infrastructure breadth

- Palo Alto PAN-OS
- GlobalProtect
- Ceph
- Kubernetes/RKE2
- Rancher
- Longhorn
- OpenProject
- Outline
- Paperless-ngx
- Dokploy
- Graylog/Loki/OpenSearch
- Cloudflare
- Authentik

## Phase 4 — Security and endpoint operations

- Cynet AiO 360
- FileWave UEM
- Microsoft Entra/M365/Defender
- Teleport
- Wazuh/TheHive/Suricata where deployed/desired
- policy-aware critical actions

## Phase 5 — AI/AIOC

- COStech Platform API
- AIOC
- MCP Gateway
- Ollama
- LiteLLM
- OpenWebUI
- Qdrant
- normalized `Ask Infra`
- evidence-backed incident explanation
- draft actions only by default

## Phase 6 — Integration Gateway

- OIDC
- tenant-aware RBAC/ABAC
- server-side credential vault references
- provider execution
- event bus
- push notifications
- audit trail
- action approval policies
- cache/rate limiting
- customer/site isolation

## Explicit non-goals before Phase 3

- no Flutter/React Native rewrite;
- no big-bang Kotlin Multiplatform migration;
- no direct AI control of critical systems;
- no deletion of working upstream integrations just to simplify architecture;
- no requirement for the central gateway in homelab mode.
