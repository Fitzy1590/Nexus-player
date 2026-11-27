# Audio DSP Rules
- DSP runs as a Media3 `AudioProcessor` on PCM only; passthrough/bitstream and tunneled playback bypass it (`DspEngine.bypass`) → disable DSP UI, show `PASSTHROUGH`/`TUNNELED`. Settings: Passthrough Auto/Off (Off = PCM + DSP), Tunneled Off/On; both apply next playback.
- Voice Boost 0–12 dB, 5-band EQ, DRC: atomic param swap, never re-init the audio sink on change.
- Software volume 0–200 %: >100 % lives in the DSP gain stage behind a sample-peak limiter (−1 dBFS ceiling = headroom for inter-sample overshoot; no oversampling) (`Player.setVolume` caps at 1.0).
- EQ visualizer bars show configured band gains, not live spectrum (no FFT tap exists yet).
