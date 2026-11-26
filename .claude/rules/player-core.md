# Player Core Rules
- ExoPlayer (Media3) primary; libmpv fallback only on decoder failure or known-unsupported codec.
- Buffer: 60 s forward / 5 s back-buffer, always capped by target bytes on low-RAM devices.
- HTTP headers enter via `DefaultHttpDataSource.Factory.setDefaultRequestProperties` (Exo) / `http-header-fields` (mpv). Never log raw header values.
- Intent schemes allowlist: http, https, content, file. Reject everything else.
- AFR: `preferredDisplayModeId` for mode switch; `Surface.setFrameRate` for seamless.
