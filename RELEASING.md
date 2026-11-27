# Releasing Nexus Player

The app checks the **public** repo `Fitzy1590/nexus-player-releases` for a newer `vX.Y.Z` release and installs its `.apk`
(Home screen and player Settings → Updates). Source code stays in this private repo; only APKs are published.

## One-time setup (on your computer)
1. Create an empty **public** GitHub repo named `nexus-player-releases`.
2. Create the release signing key. Keep it outside this repo and back it up: if you lose it, users must uninstall to update.
   ```
   keytool -genkeypair -v -keystore ~/keys/nexus-release.jks -alias nexus -keyalg RSA -keysize 4096 -validity 10000
   ```
3. Create `keystore.properties` in the repo root (gitignored, never commit it):
   ```
   storeFile=/home/you/keys/nexus-release.jks
   storePassword=...
   keyAlias=nexus
   keyPassword=...
   ```
   CI alternative: env vars `NEXUS_KEYSTORE`, `NEXUS_KEYSTORE_PASSWORD`, `NEXUS_KEY_ALIAS`, `NEXUS_KEY_PASSWORD`.
4. Install `gh` and run `gh auth login`.
5. **First install is manual.** Uninstall any debug build (different signature) and the old `com.nexus.player` app,
   then install the first signed release APK. From then on, updates install in-app.

## Each release
1. Raise `appVersion` in `app/build.gradle.kts` (versionCode is derived from it automatically).
2. Merge to `main`, then run `NOTES="What changed" scripts/release.sh`.
