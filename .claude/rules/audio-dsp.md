# Audio DSP Rules
- DSP = `androidx.media3.common.audio.AudioProcessor` chain via `DefaultAudioSink.Builder.setAudioProcessors`.
- PCM only: passthrough/bitstream bypasses DSP → disable DSP UI and show PASSTHROUGH state.
- Gain > 0 dBFS (volume > 100 %, Voice Boost) must pass through a limiter last in chain.
- Parameter changes are atomic swaps on the audio thread; no allocation in `queueInput`.
- Windows: equivalent chain via mpv `af=lavfi=[...]`.
