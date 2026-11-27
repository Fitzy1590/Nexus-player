#!/usr/bin/env bash
# Builds a signed release APK and publishes it to the public releases repo the app checks for updates.
# Run on your own machine (needs: Android SDK, gh CLI logged in, signing key per RELEASING.md).
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="Fitzy1590/nexus-player-releases"
VER=$(sed -n 's/^val appVersion = "\(.*\)"$/\1/p' app/build.gradle.kts)
[ -n "$VER" ] || { echo "Could not read appVersion from app/build.gradle.kts"; exit 1; }
[ -f keystore.properties ] || [ -n "${NEXUS_KEYSTORE:-}" ] || { echo "No signing key configured. See RELEASING.md."; exit 1; }
if gh release view "v$VER" --repo "$REPO" >/dev/null 2>&1; then
  echo "v$VER is already released. Bump appVersion in app/build.gradle.kts first."; exit 1
fi
./gradlew clean testDebugUnitTest assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "Signed APK missing (an unsigned build means the key wasn't picked up)."; exit 1; }
mkdir -p apk && OUT="apk/nexus-player-$VER.apk" && cp "$APK" "$OUT"
gh release create "v$VER" "$OUT" --repo "$REPO" --title "Nexus Player $VER" --notes "${NOTES:-Nexus Player $VER}"
echo "Published v$VER. Installed apps will offer it within 12 h, or immediately via Settings → Check for updates."
