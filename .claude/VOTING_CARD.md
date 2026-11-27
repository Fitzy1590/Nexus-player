# Visual Voting Card: Nexus Video Player Performance Architecture Update (2026-11-27)

- [ ] **1️⃣ Option A [RECOMMENDED DUAL-ENGINE SUITE]**: ExoPlayer Media3 + native libmpv Vulkan fallback + 100–500 MB memory governor (60–120 s forward) + 15 s back-buffer + zero-copy Surface + AFR + speech DSP + Obsidian HUD.
  - Justification: instant in-buffer seeking, stable buffering, OOM-safe via `onTrimMemory`.
  - Caveats: libmpv licence + NDK build open; Vulkan/DV tone-mapping and "<30 ms" are [UNCERTAIN] until measured.
- [ ] **2️⃣ Option B**: Standard ExoPlayer only (no libmpv, no new DSP).

Mockup: `VOTING_CARD.html`. Status: AWAITING VOTE (SPEC §7.4).
