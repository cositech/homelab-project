# ADR-0004: Secure credential references

Status: Accepted

Ordinary databases store only opaque `credentialRef` identifiers. Secrets live in Android Keystore/iOS Keychain or an approved gateway secret store and never appear in logs.
