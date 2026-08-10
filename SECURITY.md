# Security Policy

## Scope

Security-sensitive components include authentication, secret storage, TLS,
backup encryption, remote actions, gateway access, provider credentials and
customer isolation.

## Reporting

Do not open a public issue containing exploitable details or credentials.
Repository maintainers should configure a private GitHub security-advisory
workflow when the fork is created.

## Supported security model

- HTTPS/TLS is the default.
- HTTP/self-signed compatibility is explicit, visible and scoped.
- Secrets use platform secure storage in direct mode.
- Gateway mode uses short-lived mobile credentials and server-side provider
  secret references.
- Mutating operations are policy-controlled and audited.

## Prohibited

- credentials in fixtures;
- production tokens in issue text/screenshots;
- hard-coded secrets;
- silent TLS verification bypass;
- debug logging of full sensitive responses;
- unaudited critical actions.
