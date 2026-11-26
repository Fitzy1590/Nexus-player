# Nexus Player — SPEC.md (V2 Build)

<!-- Source of truth for reviewer.md. Confidence tags: [ESTABLISHED] [PLAUSIBLE] [UNCERTAIN] [UNKNOWN]. -->

## 0. Phase 1 Audit (2026-11-26)
- Repo state: 1 commit (`f1eb8dc`), only `README.md` (1 line). No `CLAUDE.md`, `SPEC.md`, `FEATURE_PROGRESS.md`, Gradle, or Cargo project existed. [ESTABLISHED]
- De-dup result: zero existing implementations → no feature collisions; every pillar is net-new. [ESTABLISHED]

## 1. Vote
- [x] **Option A [RECOMMENDED]** — Full 7-pillar build (dual-core decode + DSP + EQ/Voice Boost + SW volume/D-pad HUD + intent header relay + SQLite resume + AFR + Compose TV HUD).
- [ ] **Option B** — Basic video player view only (no DSP, no D-pad volume mapping).

Status: **VOTED — Option A (2026-11-26).** Phase 2 unlocked.

## 2. Targets & Stack
| Target | Stack |
|---|---|
| Android Mobile + TV | Kotlin, Media3 ExoPlayer, Jetpack Compose + `androidx.tv:tv-material` 1.0.0, Room (SQLite), libmpv (mpv-android build) |
| Windows desktop | Tauri 2 (Rust) + libmpv; DSP via mpv `--af=lavfi=[...]` |

## 3. Seven Pillars — Acceptance Criteria

### P1 Dual-Core HW Decode
- Primary: Media3 ExoPlayer with `DefaultRenderersFactory` (HW MediaCodec first). Fallback to libmpv on `PlaybackException` decoder errors or known-unsupported codec (VC-1, AV1 10-bit on devices lacking HW AV1).
- `DefaultLoadControl.Builder().setBufferDurationsMs(…, 60_000, …).setBackBuffer(5_000, true)`. [ESTABLISHED: API exists]
- Constraint: 60 s @ ~80 Mbps 4K ≈ 600 MB; must cap via `setTargetBufferBytes` + `setPrioritizeTimeOverSizeThresholds(false)` on low-RAM TV boxes. [PLAUSIBLE: arithmetic]
- "Zero-buffer" is a goal, not a guarantee — network-bound. [ESTABLISHED]

### P2 Dialogue Boost + Bass Preservation DSP
- Custom `androidx.media3.common.audio.AudioProcessor` chain injected via `DefaultAudioSink.Builder().setAudioProcessors(...)` in overridden `DefaultRenderersFactory.buildAudioSink`. [ESTABLISHED]
- Peaking/band boost 1–4 kHz + DRC compressor; low-shelf preserve 20–250 Hz; output limiter.
- **Hard constraint:** AudioProcessors run on PCM only. Passthrough/bitstream (DTS-HD, TrueHD, E-AC-3 to AVR) bypasses all DSP. UI must disable DSP controls and show "PASSTHROUGH" when active. [ESTABLISHED]

### P3 EQ Suite
- Presets: Off (Flat), Dialogue Boost, Vocal Isolation, Night Mode (DRC), Custom 5-Band.
- Voice Boost slider 0–12 dB, applied live (atomic param swap, no sink re-init).

### P4 Software Volume + D-Pad
- 0–200 % gain. `Player.setVolume` is 0.0–1.0 only → >100 % done in DSP gain stage with true-peak limiter. [ESTABLISHED: Media3 volume range]
- D-Pad Up/Down ±5 % with vertical HUD bar — **only when controls overlay is hidden** (otherwise D-pad = focus navigation). [PLAUSIBLE: avoids focus conflict]
- D-Pad Left/Right seek 5/10/15/30 s (user setting). Thumbnail preview requires source trickplay (HLS I-frame / sprite sheet); degrade to timecode-only when absent. [PLAUSIBLE]

### P5 Intent Interceptor
- `ACTION_VIEW` filters for `http(s)`, `content`, `file`, MIME `video/*`, `application/x-mpegURL`, `application/dash+xml`.
- Header extraction: support `headers` String[] key/value extra (MX Player convention). [PLAUSIBLE] Stremio/Nuvio exact extra keys: [UNKNOWN] — must be captured from a real intent dump (`adb shell dumpsys activity intents` / log) before implementation.
- Headers injected via `DefaultHttpDataSource.Factory().setDefaultRequestProperties(...)`; mpv via `http-header-fields`. [ESTABLISHED]
- `magnet:` links: neither ExoPlayer nor mpv streams torrents natively → out of scope unless a torrent engine is added. [ESTABLISHED]
- Validate URL scheme allowlist; redact `Authorization`/cookies in logs.

### P6 SQLite Resume
- Room entity: `mediaKey, positionMs, durationMs, completed, audioTrackId, subtitleTrackId, speed, updatedAt`.
- Auto-resume or prompt (setting). Speed 0.5–3.0× global + per-item override via `PlaybackParameters`.

### P7 AFR + Compose TV
- AFR: match `Display.Mode.refreshRate` to source fps via `WindowManager.LayoutParams.preferredDisplayModeId`; `Surface.setFrameRate` (API 30+) for seamless cases. [ESTABLISHED]
- Compose TV: focus scale 1.05×, 3 dp `#00F2FE` border, `snap()` transitions.

## 4. Cross-Cutting
- Diagnostic logging: toggle, default OFF, zero overhead when off; secret redaction (`Authorization`, cookies, API keys → `[REDACTED]`); 1-tap "Export Diagnostic Package" → single `.txt`.
- UI tokens: Obsidian `#0B0E14`, Mint `#10B981`, Amber `#F59E0B`, Cyan `#00F2FE`; Inter (UI), JetBrains Mono (metrics); 44 dp touch, 8 dp grid.

## 5. Open Risks
1. libmpv licensing (LGPL vs GPL build flags) — must be resolved before distribution. [UNCERTAIN]
2. Tauri 2 + libmpv window embedding on Windows (wid embed vs render API). [UNCERTAIN]
3. Stremio/Nuvio header extra format. [UNKNOWN]
