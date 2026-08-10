# Provider Model

A provider is an adapter between an external service API and normalized domain
capabilities.

A provider is identified by a stable lowercase ID, e.g. `proxmox-ve`.

Provider metadata declares:

- name;
- category;
- connectivity modes;
- authentication modes;
- capabilities;
- maturity;
- supported API/version detection method;
- minimum privileges;
- risk-bearing actions.

Provider implementations must not dictate top-level navigation.

## Maturity

- `legacy`: upstream implementation, not yet capability-adapted
- `experimental`: capability provider, incomplete compatibility
- `beta`: contract/fixture tests, broad real-world validation pending
- `stable`: compatibility matrix and action/security gates satisfied
