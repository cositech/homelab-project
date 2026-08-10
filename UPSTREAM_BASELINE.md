# Upstream Baseline

## Repository

| Attribute | Baseline |
|---|---|
| Upstream repository | `JohnnWi/homelab-project` |
| Maintained fork | `cositech/homelab-project` |
| Default branch | `main` |
| Upstream state | archived |
| Final upstream commit used by the fork | `75e7f3d53e2ec73ff55ce95927ddc173c724c9fd` |
| License | Apache-2.0 |
| Primary languages | Kotlin, Swift |
| Android package | `com.homelab.app` |
| Current app version | `1.6.2` |
| Current build/version code | `39` |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI |
| Final README integrations | 34 |

## Current source/build baseline

Android:

- compileSdk `36`;
- targetSdk `36`;
- minSdk `26`;
- Java source/target `17`;
- Android Gradle Plugin `9.0.1`;
- Kotlin `2.1.10`;
- Gradle wrapper `9.4.0`;
- upstream GitHub Actions provisions JDK `21`.

iOS:

- Swift `6.0` in `project.yml`;
- iOS deployment target `26.0`;
- Xcode project committed at `HomelabSwift/Homelab.xcodeproj`;
- `HomelabSwift/project.yml` retained alongside it;
- upstream CI builds the committed Xcode project directly on `macos-26`.

## Existing development commands

Android:

```bash
cd HomelabAndroid
./gradlew --no-daemon --console=plain :app:testDebugUnitTest
./gradlew --no-daemon --console=plain :app:assembleDebug
```

iOS on macOS:

```bash
mkdir -p HomelabSwift/Config
cat > HomelabSwift/Config/Signing.xcconfig <<'EOF'
DEVELOPMENT_TEAM =
PRODUCT_BUNDLE_IDENTIFIER = homelab.foreverhomelab
EOF

cd HomelabSwift
xcodebuild build \
  -project Homelab.xcodeproj \
  -scheme Homelab \
  -configuration Debug \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO
```

## Observed upstream CI behavior

The final upstream CI:

- verifies Android/iOS version consistency;
- rejects tracked `.ipa`, `.apk` and `.aab` release binaries;
- compiles Android Debug Kotlin;
- builds the iOS application without signing;
- does not execute the Android unit-test suite;
- does not execute the iOS unit-test suite;
- does not include CodeQL, dependency review or Dependabot configuration.

Phase 0 preserves the upstream invariants and adds explicit unit-test/security/dependency gates.

## Test baseline

Android unit-test sources observed include authentication, URL fallback, compatibility parsing and selected provider repository/parser tests. iOS includes Keychain and model-decoding unit tests. The Phase-0 CI turns these existing tests into required execution gates before introducing any coverage percentage target.
