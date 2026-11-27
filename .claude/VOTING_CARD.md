# Visual Voting Card: Nexus Player Cinema Glass UI/UX Upgrade (2026-11-27)

- [ ] **1. Option A [RECOMMENDED: MATERIAL CINEMA GLASS HUD]**: Deep Obsidian `#0B0E14`, translucent glass transport bar, `#00F2FE` cyan TV focus border, vertical volume HUD, Sound side-drawer (Voice Boost 0–12 dB, DRC, 5-band EQ), 0 ms snap drawers.
  - Justification: builds on the existing `Hud.kt` tokens (smallest delta); one consistent look for touch, mouse and D-pad.
  - Caveat: real backdrop blur on Windows only; Android uses a 75 % tint (Compose can't blur a SurfaceView video).
- [ ] **2. Option B [MATERIAL 3 ADAPTIVE MEDIA SURFACES]**: M3 tonal surfaces, tonal control cards, standard drawer menus.
- [ ] **3. Option C [MINIMALIST MONOCHROME HUD]**: `#000000` black glass, sharp white outline controls, monospace timecode focus.

Sub-decisions: applicationId rename (keep `com.nexus.player` / `com.fitzy.nexusplayer`) · violet `#7C3AED` accent (yes/no) · seek steps (−10/+10, hold +30 vs keep 5/10/15/30 setting).

Mockup: `VOTING_CARD.html`. Status: AWAITING VOTE.
