# Provider specification: Grafana

- Provider ID: `grafana`.
- Domain and owner: visualization and observability inventory; Grafana Labs.
- Supported versions: current Grafana releases exposing the HTTP APIs below. The `/api` search and data-source endpoints are legacy APIs and remain a compatibility boundary because Grafana begins deprecating legacy APIs in Grafana 13 in favor of `/apis`.
- Delivery mode: direct HTTPS; gateway delivery can be added for inaccessible or centrally governed environments.
- Authentication and minimum scopes: organization-scoped service account token sent as `Authorization: Bearer`. Use a read-only role plus dashboard search and `datasources:read` permissions for the intended organization.
- TLS modes: system trust, custom CA, certificate pinning and explicitly enabled insecure compatibility mode.
- Declared capabilities: health, resources and metrics. Events and all action capabilities are absent from the initial slice.
- Resource mappings: dashboards become normalized `dashboard` assets with UID, title, folder and tags. Data sources become `data-source` assets with UID, name, type, default and read-only flags.
- Event/severity mappings: authentication, permission and connectivity failures produce unavailable health. Grafana Alerting is not queried, so no Grafana alert events are declared.
- Actions and risk classes: none in Phase 2. Dashboard, folder, data-source, alert and service-account mutations are not exposed.
- Tenant/site/customer scoping: one configured instance represents one Grafana organization as constrained by the service account token.
- Sensitive fields and redaction: tokens remain in the platform secure credential store. Data-source URLs, access settings, secure JSON fields and credential references are never copied into normalized operations state.
- Operational dashboards: Operations shows provider health plus dashboard and data-source inventory. Home shows dashboard versus data-source counts.
- Known limitations: no Grafana Alerting, annotations, folders as independent assets, dashboard JSON, panel queries or new Kubernetes-style `/apis` implementation yet.

## Read-only API surface

- `GET /api/health`
- `GET /api/search?type=dash-db&limit=1000&page=1`
- `GET /api/datasources`

The client uses only fixed GET requests and does not proxy arbitrary dashboard or data-source queries.
