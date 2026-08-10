# Security Architecture

## Direct mode

```text
ServiceInstance(non-secret) -> credentialRef -> SecureCredentialStore
```

Android target: Keystore-backed encryption.
iOS target: Keychain.

## Gateway mode

```text
Mobile --OIDC/short-lived--> Gateway --secretRef--> Vault/provider credential
```

The mobile client receives no provider long-lived secret.

## TLS trust profiles

- `system`
- `custom_ca`
- `certificate_pin`
- `insecure_compatibility`

HTTP and insecure TLS are compatibility features, not implicit fallback paths.

## Actions

Policy input includes:

- user identity;
- organization/site;
- target resource;
- provider/action;
- environment/criticality tags;
- requested arguments;
- current health/incident context.

Policy output includes allow/deny, required confirmation and approval level.
