# Player Core Rules
- ExoPlayer (Media3) primary; libmpv fallback only on decoder failure or known-unsupported codec.
- Buffer: continuous fill (min = max = 180 s), 30 s back-buffer, capped at heap/2 (≤768 MB) target bytes.
- HTTP via OkHttp (pooled connections). Seeks coalesced (SeekAccumulator); ≥60 s jumps use CLOSEST_SYNC.
- External launches: return MX (`position`/`duration`/`end_by`) + VLC (`extra_*`) result on every exit.
- HTTP headers enter via `OkHttpDataSource.Factory.setDefaultRequestProperties` (Exo) / `http-header-fields` (mpv). Never log raw header values.
- Intent schemes allowlist: http, https, content, file. Reject everything else.
- AFR: `preferredDisplayModeId` for mode switch; `Surface.setFrameRate` for seamless.
