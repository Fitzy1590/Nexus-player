# FEATURE_PROGRESS.md

## Phase 1 — Audit & Voting Card
- [x] Repository audit (empty repo; no existing features to de-dup)
- [x] SPEC.md drafted
- [x] VOTING_CARD.html + .claude/VOTING_CARD.md generated
- [x] Governance: CLAUDE.md, .claude/rules/*, .claude/settings.json, agents
- [x] **User vote** — Option A [x] / Option B [ ] (2026-11-26)

## Phase 2 — Build
| # | Pillar | Status |
|---|---|---|
| P1 | Dual-core Media3 + libmpv decode, 60 s / 5 s buffer | 🟡 Media3 core done (60 s fwd / 5 s back, heap-capped bytes, decoder fallback); libmpv deferred |
| P2 | Dialogue Boost + Bass Preservation DSP | ✅ DspEngine + AudioProcessor; unit-tested |
| P3 | EQ presets + Voice Boost slider | ✅ 5 presets, Voice Boost 0–12 dB, custom 5-band |
| P4 | SW volume 0–200 % + D-pad HUD / seek | ✅ 0–200 % + limiter, D-pad HUD, 5/10/15/30 s seek; thumbnails → timecode fallback |
| P5 | VIEW intent interceptor + header relay | 🟡 VIEW filter + `headers` String[] / Browser.EXTRA_HEADERS; Stremio/Nuvio keys unverified |
| P6 | Room/SQLite resume + track/speed state | ✅ Room resume, track ids, global/per-item speed |
| P7 | AFR + Compose TV HUD | ✅ AFR mode switch + Compose TV HUD (tv-material 1.0.0) |
| X1 | Diagnostic logging + export | ✅ toggle, redaction, export .txt |
| X2 | Windows Tauri 2 + libmpv shell | ⬜ todo |

## Iteration 2 (2026-11-26)
- [x] Touch taps on HUD buttons (tv-material ignores touch — proven by HudTouchTest)
- [x] Retry clears error; double-tap edge seek + chained taps
- [x] Buffer: continuous fill, 180 s / 30 s back, heap/2 cap, OkHttp
- [x] Seeking: coalesced targets, hold-accelerate, focusable/touch scrub bar, sync seeks for ≥60 s jumps
- [x] Pinch-to-zoom 100–300 %
- [x] Caller handoff: result on completion/back (MX + VLC extras), caller start position, 10 s autosave
- [ ] Device-verify Stremio/Nuvio read the result extras

## Verification (2026-11-26)
- `./gradlew assembleDebug testDebugUnitTest lintDebug --offline` → BUILD SUCCESSFUL; 18/18 unit tests pass; lint 0 errors.
- Not verified on device: no emulator (no KVM) in build container. Needs a real Android TV / phone smoke test.

## Phase 3 — Cinema Glass UI/UX Redesign (2026-11-27)
- [x] UI audit (SPEC §6.1–6.2)
- [x] VOTING_CARD.html + .claude/VOTING_CARD.md regenerated for UI vote
- [x] Governance: `.claude/rules/audio-dsp.md` added; Stop hook (SDK-guarded, pipefail) added
- [x] **User vote** — Option A [x] / B [ ] / C [ ] · rename to com.fitzy.nexusplayer · violet accent · keep 5/10/15/30 seek (2026-11-27)
- [x] applicationId → `com.fitzy.nexusplayer` (namespace/Kotlin packages stay `com.nexus.player`)
- [x] Tokens: Cinema Glass 75 % + white hairline, violet selected state; Outfit / Inter / JetBrains Mono bundled in `res/font`
- [x] Icon + label transport bar (`HudIcons`), larger mono timecode
- [x] Right-edge Sound drawer: presets, Voice Boost, Night mode (DRC) toggle, volume, 5-band EQ bars
- [x] Format badges (4K/HDR10/HLG/Dolby Vision · fps · Mbps), seek chip `+30s ▸ 01:13:14`
- [x] `./gradlew assembleDebug testDebugUnitTest lintDebug` → BUILD SUCCESSFUL, 34 tests pass, lint 0 errors (2026-11-27)
- [ ] Visual/device check on Android TV + phone (no emulator; Robolectric screen capture timed out)
- [ ] Windows Tauri 2 shell (X2) — not started; libmpv embedding risk open (SPEC §5)

## In-app updates (2026-11-27)
- [x] `Updater`: checks this repo's GitHub Releases (`Fitzy1590/Nexus-player`, must be public) latest release (12 h throttle + manual), downloads APK, verifies GitHub sha256 digest when present, installs via PackageInstaller session
- [x] Update row on Home + player Settings; `REQUEST_INSTALL_PACKAGES`; unknown-sources prompt
- [x] Release signing via `keystore.properties` / `NEXUS_*` env; versionCode derived from `appVersion` (0.2.0); `scripts/release.sh`, `RELEASING.md`
- [x] Debug + release (R8) build, 38 unit tests, lint 0 errors
- [ ] User: make repo public + signing key; first signed install; device-test an update

## Known gaps
- libmpv fallback + Windows Tauri shell pending (licensing / embedding risks, SPEC §5).

## 2026-11-27 playback tuning
- [x] Speeds 0.5–3.0× in 0.1 steps; seek settle 200 ms; resume after 0.5 s / 1 s rebuffer
- [x] Buffer ahead setting 3 / 10 / 20 / 30 min (heap/2 byte cap unchanged)
- [x] Tunneled playback (off by default, DSP bypassed), Passthrough Auto/Off, display HDR types in Stats
- [x] Speeds 0.05 steps 0.5–1.3×, then 0.1 to 3.0×; seek settle 80 ms; CLOSEST_SYNC ≥15 s; rebuffer resume 250 ms / 500 ms; defaults: buffer 10 min, auto-resume on
