# ADR-0005: Explicit TLS policy

Status: Accepted

Connections use `SYSTEM`, `CUSTOM_CA`, `CERTIFICATE_PIN`, or visible `INSECURE_COMPATIBILITY`. A provider-wide boolean trust bypass is not an acceptable long-term interface.
