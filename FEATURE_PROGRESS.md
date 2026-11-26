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

## Verification (2026-11-26)
- `./gradlew assembleDebug testDebugUnitTest lintDebug --offline` → BUILD SUCCESSFUL; 18/18 unit tests pass; lint 0 errors.
- Not verified on device: no emulator (no KVM) in build container. Needs a real Android TV / phone smoke test.

## Known gaps
- Inter / JetBrains Mono not bundled (font hosts returned 403); system sans/mono used.
- libmpv fallback + Windows Tauri shell pending (licensing / embedding risks, SPEC §5).
