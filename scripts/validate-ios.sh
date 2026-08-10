#!/usr/bin/env bash
set -euo pipefail
command -v xcodebuild >/dev/null || { echo "xcodebuild is required (macOS/Xcode)"; exit 2; }
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root/HomelabSwift"
xcodebuild test \
  -project Homelab.xcodeproj \
  -scheme Homelab \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath /tmp/homelab-ios-test \
  CODE_SIGNING_ALLOWED=NO
xcodebuild build \
  -project Homelab.xcodeproj \
  -scheme Homelab \
  -configuration Debug \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath /tmp/homelab-ios-build \
  CODE_SIGNING_ALLOWED=NO
