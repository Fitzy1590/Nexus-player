# Media Engine Rules (proposed in SPEC §7; binding once §7.4 vote is `[x]`)
- Package `com.nexus.player.media` (not `src/media/`). Exo stays primary; `DualPlaybackEngine` routes to libmpv only on decoder failure or known-unsupported codec (extends `player-core.md`).
- Memory governor: forward byte cap from `ActivityManager.getMemoryInfo()` (100–500 MB), time ceiling = user buffer setting; `onTrimMemory(RUNNING_CRITICAL)` → forward target 15 s + drop back-buffer. Exo allocator lives in the Java heap: never exceed heap/2.
- Back-buffer 15 s via `setBackBuffer(…, retainFromKeyframe = true)`; backward seeks inside it must issue zero HTTP requests (test with the OkHttp interceptor).
- libmpv: `demuxer-max-bytes` / `demuxer-max-back-bytes` mirror the governor; JNI under `app/src/main/cpp`, ABI arm64-v8a + armeabi-v7a; licence (LGPL build) resolved before any release.
- Latency claims ("<30 ms seek") are targets, measured and logged in `FEATURE_PROGRESS.md`; never asserted untested.
- Vulkan/HDR tone-mapping is libmpv-only (`gpu-next`); Exo path keeps MediaCodec→Surface (already zero-copy).
