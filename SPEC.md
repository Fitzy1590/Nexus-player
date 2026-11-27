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
- `DefaultLoadControl`: min = max = user setting 3 (default) / 10 / 20 / 30 min (continuous refill), start 0.5 s / rebuffer 1 s, back-buffer 30 s. Byte cap heap/2 ≤ 768 MB is the effective limit for 4K. (Revised 2026-11-26 per user request; was 60 s / 5 s.)
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

## 6. UI/UX Redesign — Cinema Glass (Phase 1 audit, 2026-11-27)

### 6.1 Audit of current UI (`app/src/main/java/com/nexus/player/ui/`)
- Tokens already in `Hud` object: Obsidian `#0B0E14`, Cyan `#00F2FE`, Mint `#10B981`, Amber `#F59E0B`; panel `#CC121822`; 8 dp radius glass = tint + 1 dp cyan-16 % border, **no blur**. [ESTABLISHED: Hud.kt]
- Overlays toggle via plain `if (...)` blocks — no `AnimatedVisibility`/`tween` anywhere → already 0 ms snap. [ESTABLISHED: grep of app/src/main/java]
- Transport: single bottom glass card (seek bar + timecode + `LazyRow` of 10 text buttons: −Ns, Play, +Ns, Sound, Audio, Subs, Speed, Stats, Settings, Zoom reset); panels open **above** the bar, not as a side drawer. [ESTABLISHED: PlayerScreen.kt:287–335]
- TV focus: `HudButton` = tv-material `Button`, 1.05× scale, 3 dp cyan border, ≥44 dp. Seek bar + `StepSlider` mirror it manually. [ESTABLISHED: Hud.kt]
- Volume HUD: right-edge 56×280 dp glass column, 0–200 %, amber above 100 %. Seek HUD: centered timecode text only. [ESTABLISHED: PlayerScreen.kt:242–262]
- Fonts: system Sans/Monospace stand-ins; Inter / JetBrains Mono not bundled. [ESTABLISHED: Hud.kt comment, FEATURE_PROGRESS]
- Icons: none — every control is a text label. [ESTABLISHED]
- Windows (Tauri 2) shell: does not exist yet (FEATURE_PROGRESS X2 ⬜). Desktop "redesign" = new build. [ESTABLISHED]
- Package/applicationId is `com.nexus.player`, not `com.fitzy.nexusplayer`. [ESTABLISHED: app/build.gradle.kts]

### 6.2 Technical constraints on the requested design
- Compose has no CSS-style `backdrop-filter`; `Modifier.blur` blurs the composable's own content (API 31+). Real backdrop blur needs a library (e.g. Haze) and cannot sample a `SurfaceView` video layer. [PLAUSIBLE: SurfaceView renders on a separate compositor layer] → Android glass = translucent tint + hairline border; true blur only on Windows WebView2 (CSS `backdrop-filter`). I'm not certain — verify before acting.
- Scrub thumbnails need source trickplay (HLS I-frame playlist / sprite sheet); fallback remains timecode-only. [ESTABLISHED: SPEC §3 P4]
- EQ "visualizer bars" can show configured band gains; a live spectrum needs a new PCM FFT tap in `DspEngine`. [ESTABLISHED: no FFT code in repo]
- Violet `#7C3AED` as a solid accent is compatible with the anti-slop rule (which bans purple→pink *gradients*), but adds a 5th accent. [PLAUSIBLE]

### 6.3 Vote
- [x] **Option A [RECOMMENDED] — Material Cinema Glass HUD**: Obsidian `#0B0E14`, translucent glass transport bar, right-side Sound drawer (Voice Boost 0–12 dB, DRC, 5-band EQ bars), vertical volume HUD, `#00F2FE` 3 dp focus, icon+label buttons, 0 ms snap.
- [ ] **Option B — Material 3 Adaptive Media Surfaces**: M3 tonal surfaces, tonal control cards, standard bottom-sheet/drawer menus.
- [ ] **Option C — Minimalist Monochrome HUD**: `#000000` black glass, sharp white outline controls, monospace timecode focus.
- Sub-decisions: [x] rename applicationId → `com.fitzy.nexusplayer` (breaks in-place update / Room data of installed builds) · [x] include Violet `#7C3AED` secondary accent (selected tracks / active presets) · [x] keep current 5/10/15/30 s seek setting.

Status: **VOTED — Option A (2026-11-27).** Phase 3 build unlocked.

## 7. Performance Engine Update — Dual-Engine, Memory Governor, Fast Seek (Phase 1 audit, 2026-11-27)

### 7.1 Audit vs request (`app/src/main/java/com/nexus/player/`)
- Already built: Media3 + `DefaultLoadControl` (user buffer 3/10/20/30 min, default 10; 30 s back; byte cap heap/2 ≤ 768 MB); `SeekAccumulator` + `CLOSEST_SYNC` ≥15 s, 80 ms settle; `DspEngine` (Voice Boost 0–12 dB, 5-band EQ, DRC, 0–200 % limiter, passthrough/tunneled bypass); AFR via `preferredDisplayModeId`; `PlayerView` (SurfaceView default → MediaCodec→Surface is already zero-copy); Cinema Glass HUD. [ESTABLISHED: PlayerFactory.kt, PlaybackController.kt]
- Missing: libmpv/NDK/JNI (no `cpp/`), `DualPlaybackEngine`, `onTrimMemory`/`getMemoryInfo` use, in-RAM cue index, sprite-sheet trick-play, Vulkan tone-mapping. [ESTABLISHED: grep]
- Request paths `src/media/…` do not exist → use `com.nexus.player.media.*`. `ui-hud.md` request = existing `tv-ui.md` (no duplicate). `audio-dsp.md` already exists (unchanged).

### 7.2 Conflicts / constraints
- Buffer: request 60–120 s fwd, 100–500 MB, 15 s back vs current user-set 10 min, ≤768 MB, 30 s back. Proposal: governor sizes bytes (100–500 MB), time ceiling stays the user setting (add 1/2 min presets), back-buffer 15 s. [PLAUSIBLE]
- Exo has no "demuxer packet ring buffer"; its `SampleQueue`/`Allocator` is the equivalent. mpv exposes `demuxer-max-bytes`/`demuxer-max-back-bytes`. [ESTABLISHED]
- "<30 ms" seek: network-independent only for in-buffer targets; untested. [UNCERTAIN]
- libmpv on Android via Vulkan (`gpu-next`) and Dolby Vision P5/P7/P8 mapping: build/driver dependent. [UNCERTAIN — verify before acting]
- libmpv licence (LGPL vs GPL) and NDK build cost: SPEC §5 risk 1 still open. [UNCERTAIN]
- Dolby Atmos/DTS-HD passthrough already follows `audio-dsp.md`; 32-bit float = `setEnableFloatOutput` (device dependent). [PLAUSIBLE]

### 7.3 Scope of build if approved
Governor + trim hook + 15 s back-buffer; cue-index cache + sprite trick-play; `DualPlaybackEngine` + libmpv JNI (staged, behind licence gate); gesture/engine-toggle HUD additions.

### 7.4 Vote
- [ ] **Option A [RECOMMENDED] — Dual-engine suite**: Exo + libmpv Vulkan fallback + memory governor (100–500 MB, 15 s back, trim hook) + fast seek/cue cache/sprites + AFR + speech DSP + Obsidian HUD.
- [ ] **Option B** — Standard Exo only (no libmpv, no new DSP); governor + seek work limited to Exo.

Status: **AWAITING VOTE.** No app code until one box is `[x]`.
