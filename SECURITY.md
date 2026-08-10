# Security policy

## Reporting

Do not disclose vulnerabilities in public issues. Use GitHub private vulnerability reporting for this repository. If that is unavailable, contact the repository owner through a private, previously verified channel.

Include affected version/commit, platform, prerequisites, impact, reproduction steps, and suggested mitigation. Never include production credentials or customer data.

## Supported code

Security fixes target the current `main` branch and the latest published release. Older releases may receive fixes only when migration is not practical.

## Security invariants

- Secrets never enter source control, logs, crash reports, analytics, unencrypted backups, or normal application databases.
- TLS verification is enabled by default. Compatibility bypasses are explicit, scoped to one provider instance, visually warned, and excluded from background high-risk actions.
- Provider data and credentials are tenant scoped before MSP features are enabled.
- Mutating actions require least privilege, confirmation proportional to impact, and an auditable result.
- Backup exports containing credentials are authenticated-encrypted and must not reveal secret metadata before successful decryption.

See `docs/security/SECURITY_AUDIT.md` and `docs/security/THREAT_MODEL.md`.
