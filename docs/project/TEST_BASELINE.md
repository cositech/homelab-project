# Test baseline

## Existing tests

- Android: `HomelabAndroid/app/src/test`
- iOS: `HomelabSwift/HomelabTests`

The previous CI compiled both apps but did not execute their unit-test suites. Phase 0 makes tests and builds separate required jobs so a successful compilation cannot mask test regressions.

## Required checks

| Change | Required validation |
|---|---|
| Documentation/schemas | static audit and schema examples |
| Android logic/network/storage | Android unit tests and compile |
| iOS logic/network/storage | iOS unit tests and compile |
| Shared contract/security/release | all static, Android, and iOS checks |

Local iOS validation needs macOS/Xcode. GitHub Actions is the authoritative cross-platform exit gate when development runs on Windows/Linux.
