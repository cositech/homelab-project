# Upstream baseline

- Upstream: `https://github.com/JohnnWi/homelab-project.git`
- Fork: `https://github.com/cositech/homelab-project.git`
- Baseline branch: `main`
- License: Apache-2.0
- Clients: Kotlin/Jetpack Compose Android and Swift/SwiftUI iOS
- Upstream product surface: 34 service dashboards, multi-instance connections, encrypted backup/restore, biometric lock, bookmarks and AltStore/SideStore metadata.

The fork preserves Git history and keeps `upstream` read-only. Upstream changes are reviewed and integrated deliberately; they are never force-synced over fork-specific architecture or security work.

Record the exact Phase-0 parent commit in the pull request and release notes rather than hard-coding a SHA in this document.
