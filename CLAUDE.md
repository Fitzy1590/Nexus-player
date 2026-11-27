# Nexus Player — CLAUDE.md
<!-- Keep < 200 lines. Domain rules live in .claude/rules/. -->

- Source of truth: `SPEC.md`. Progress: `FEATURE_PROGRESS.md`.
- Engine update rules: `.claude/rules/media-engine.md` (vote SPEC.md §7.4).
- Phase gate: no app code until the vote in SPEC.md §1 is `[x]`.
- Domain rules: `.claude/rules/player-core.md`, `audio-dsp.md`, `tv-ui.md`.

## Guardrails
- Read before edit; minimal diffs; never reformat untouched code.
- No unasked abstractions. Audit existing features before adding modules.
- Bugs: prove with failing test/repro first. Scratch files only in `temp/` or `.claude/scratch/`.
- Build: `./gradlew assembleDebug testDebugUnitTest lintDebug --offline 2>&1 | head -50` (SDK: compileSdk 36, AGP 8.13.2, Kotlin 2.2.21).
- `cargo check 2>&1 | head -50` for Windows shell.
- Redact `Authorization`, cookies, API keys in any log as `[REDACTED]`.

## Output
- No preamble. Task summaries ≤ 2 sentences.
