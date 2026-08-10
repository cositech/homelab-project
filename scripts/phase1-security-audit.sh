#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  grep -Eq "$pattern" "$file" || fail "$file does not match required pattern: $pattern"
}

reject_pattern() {
  local pattern="$1"
  local file="$2"
  if grep -Eq "$pattern" "$file"; then
    fail "$file matches forbidden pattern: $pattern"
  fi
}

ANDROID_ROOT="HomelabAndroid/app/src/main"
IOS_ROOT="HomelabSwift/Homelab"
ENTITY="$ANDROID_ROOT/java/com/homelab/app/data/local/entity/ServiceInstanceEntity.kt"
ANDROID_CONNECTION="$ANDROID_ROOT/java/com/homelab/app/domain/model/ServiceConnection.kt"
ANDROID_LOGIN="$ANDROID_ROOT/java/com/homelab/app/ui/login/ServiceLoginScreen.kt"
ANDROID_LOGIN_VM="$ANDROID_ROOT/java/com/homelab/app/ui/login/ServiceLoginViewModel.kt"
ANDROID_STORE="$ANDROID_ROOT/java/com/homelab/app/security/SecureCredentialStore.kt"
IOS_CONNECTION="$IOS_ROOT/Models/ServiceConnection.swift"
IOS_KEYCHAIN="$IOS_ROOT/Services/KeychainService.swift"
IOS_LOGIN="$IOS_ROOT/Views/ServiceLogin/ServiceLoginView.swift"

reject_pattern 'val (token|proxmoxCsrfToken|proxmoxOtp|apiKey|piholePassword|password|customCaPem):' "$ENTITY"
require_pattern 'val credentialRef: String' "$ENTITY"
require_pattern 'version = 7' "$ANDROID_ROOT/java/com/homelab/app/data/local/AppDatabase.kt"
require_pattern 'migration6To7' "$ANDROID_ROOT/java/com/homelab/app/di/DatabaseModule.kt"
require_pattern 'AndroidKeyStore' "$ANDROID_STORE"
require_pattern 'AES/GCM/NoPadding' "$ANDROID_STORE"

for mode in SYSTEM CUSTOM_CA CERTIFICATE_PIN INSECURE_COMPATIBILITY; do
  require_pattern "$mode" "$ANDROID_CONNECTION"
  require_pattern "$mode" "$IOS_CONNECTION"
done

require_pattern 'usesCleartextTraffic="false"' "$ANDROID_ROOT/AndroidManifest.xml"
reject_pattern 'usesCleartextTraffic="true"' "$ANDROID_ROOT/AndroidManifest.xml"
require_pattern 'cleartextTrafficPermitted="false"' "$ANDROID_ROOT/res/xml/network_security_config.xml"
reject_pattern 'cleartextTrafficPermitted="true"' "$ANDROID_ROOT/res/xml/network_security_config.xml"
require_pattern 'mutableStateOf\(false\)' "$ANDROID_LOGIN"
require_pattern 'allowSelfSigned: Boolean = false' "$ANDROID_LOGIN_VM"
require_pattern '@State private var allowSelfSigned = false' "$IOS_LOGIN"

python3 - <<'PY'
from pathlib import Path
import plistlib

path = Path("HomelabSwift/Homelab/Info.plist")
with path.open("rb") as handle:
    info = plistlib.load(handle)
ats = info.get("NSAppTransportSecurity", {})
for key in ("NSAllowsArbitraryLoads", "NSAllowsArbitraryLoadsForMedia", "NSAllowsArbitraryLoadsInWebContent"):
    if ats.get(key) is True:
        raise SystemExit(f"FAIL: {key} must not be enabled")
PY

require_pattern 'kSecAttrAccessibleWhenUnlockedThisDeviceOnly' "$IOS_KEYCHAIN"
require_pattern 'ServiceCredentialEnvelope' "$IOS_KEYCHAIN"
require_pattern 'ServiceInstanceMetadata' "$IOS_CONNECTION"

require_pattern 'object ProviderRegistry' "$ANDROID_ROOT/java/com/homelab/app/domain/provider/ProviderCore.kt"
require_pattern 'enum ProviderRegistry' "$IOS_ROOT/Models/ServiceType.swift"
require_pattern 'PROXMOX' "$ANDROID_ROOT/java/com/homelab/app/domain/provider/ProviderCore.kt"
require_pattern 'UPTIME_KUMA' "$ANDROID_ROOT/java/com/homelab/app/domain/provider/ProviderCore.kt"
require_pattern 'case \.proxmox' "$IOS_ROOT/Models/ServiceType.swift"
require_pattern 'case \.uptimeKuma' "$IOS_ROOT/Models/ServiceType.swift"

echo "Phase 1 security audit passed"
