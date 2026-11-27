# TV / UI Rules
- Compose TV `androidx.tv:tv-material` 1.0.0. Focus: scale 1.05×, 3 dp border `#00F2FE`.
- Transitions: `snap()` (0 ms) for tabs and overlays.
- Tokens (`Hud.kt`): bg `#0B0E14`, glass `#BF0B0E14` + 12 % white hairline, cyan `#00F2FE` focus, violet `#7C3AED` = selected only, mint `#10B981` / amber `#F59E0B` = metrics. Bundled fonts: Outfit (titles), Inter (UI), JetBrains Mono (timecode/metrics).
- Icons: `HudIcons` vectors via `HudButton(icon=, label=)`; no icon libraries, no emoji.
- 44 dp min touch target, 8 dp grid. No gray boxes, purple-pink gradients, glow blobs, emoji buttons.
- D-pad Up/Down = volume only while controls overlay hidden; otherwise focus navigation.
