# Audio DSP Rules
- DSP runs as a Media3 `AudioProcessor` on PCM only; passthrough/bitstream bypasses it → disable DSP UI, show `PASSTHROUGH`.
- Voice Boost 0–12 dB, 5-band EQ, DRC: atomic param swap, never re-init the audio sink on change.
- Software volume 0–200 %: >100 % lives in the DSP gain stage behind a true-peak limiter (`Player.setVolume` caps at 1.0).
- EQ visualizer bars show configured band gains, not live spectrum (no FFT tap exists yet).
