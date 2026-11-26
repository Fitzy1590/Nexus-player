package com.nexus.player

import com.nexus.player.audio.DspEngine
import com.nexus.player.audio.DspParams
import com.nexus.player.audio.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class DspEngineTest {
    private val sr = 48_000

    /** RMS gain in dB of a stereo sine through the engine, measured after 0.5 s settling. */
    private fun gainDb(p: DspParams, freq: Float, amp: Float = 0.05f, ch: Int = 2, onlyCh: Int = 0): Float {
        val e = DspEngine().apply { configure(sr, ch); params = p }
        val frames = sr
        val buf = FloatArray(frames * ch) { i -> amp * sin(2 * PI * freq * (i / ch) / sr).toFloat() }
        e.process(buf, frames, ch)
        var sIn = 0.0; var sOut = 0.0
        for (f in frames / 2 until frames) {
            val x = amp * sin(2 * PI * freq * f / sr)
            sIn += x * x; sOut += buf[f * ch + onlyCh].toDouble().let { it * it }
        }
        return (20 * log10(sqrt(sOut / sIn))).toFloat()
    }

    @Test fun flatIsTransparent() {
        assertEquals(0f, gainDb(DspParams(), 1000f), 0.05f)
    }

    @Test fun voiceBoostLiftsVocalBandNotSubBass() {
        val p = DspParams(voiceBoostDb = 12f)
        val vocal = gainDb(p, 2000f)
        val sub = gainDb(p, 40f)
        assertEquals(12f, vocal, 0.3f)
        // Bass preservation shelf: sub-bass lifted (~+4 dB), never cut, and well below vocal lift.
        assertTrue("sub=$sub", sub in 2f..6f)
    }

    @Test fun vocalIsolationAttenuatesSideSignal() {
        val e = DspEngine().apply { configure(sr, 2); params = DspParams(preset = Preset.VOCAL_ISOLATION) }
        val frames = sr / 2
        // Pure side signal (L = -R) at 1 kHz.
        val buf = FloatArray(frames * 2) { i -> val s = 0.05f * sin(2 * PI * 1000 * (i / 2) / sr).toFloat(); if (i % 2 == 0) s else -s }
        val inPeak = 0.05f
        e.process(buf, frames, 2)
        var peak = 0f
        for (i in buf.size / 2 until buf.size) peak = max(peak, abs(buf[i]))
        assertTrue("side peak=$peak", peak < inPeak * 0.5f)
    }

    @Test fun dialogueOnlyOnCentreForSurround() {
        val p = DspParams(voiceBoostDb = 12f)
        assertEquals(12f, gainDb(p, 2000f, ch = 6, onlyCh = 2), 0.3f) // FC
        assertEquals(0f, gainDb(p, 2000f, ch = 6, onlyCh = 0), 0.3f)  // FL untouched in vocal band
    }

    @Test fun volume200PercentIsLimitedBelowCeiling() {
        val e = DspEngine().apply { configure(sr, 2); params = DspParams(volume = 2f) }
        val buf = FloatArray(sr * 2) { i -> 0.9f * sin(2 * PI * 440 * (i / 2) / sr).toFloat() }
        e.process(buf, sr, 2)
        assertTrue(buf.all { abs(it) <= DspEngine.CEILING })
    }

    @Test fun volumeScalesQuietSignal() {
        assertEquals(6.02f, gainDb(DspParams(volume = 2f), 1000f, amp = 0.01f), 0.1f)
        assertEquals(-6.02f, gainDb(DspParams(volume = 0.5f), 1000f), 0.1f)
    }

    @Test fun nightModeCompressesLoudMoreThanQuiet() {
        val p = DspParams(preset = Preset.NIGHT)
        val quiet = gainDb(p, 300f, amp = 0.005f)
        val loud = gainDb(p, 300f, amp = 0.5f)
        assertTrue("quiet=$quiet loud=$loud", quiet - loud > 6f)
    }

    @Test fun customEqBandsApply() {
        val p = DspParams(preset = Preset.CUSTOM, eqDb = listOf(0f, 0f, 0f, -9f, 0f))
        assertEquals(-9f, gainDb(p, 4000f), 0.3f)
        assertEquals(0f, gainDb(p, 60f), 0.5f)
    }

    @Test fun paramSwapTakesEffectWithoutReconfigure() {
        val e = DspEngine().apply { configure(sr, 2) }
        val buf = FloatArray(200) { 0.1f }
        e.process(buf, 100, 2)
        e.params = DspParams(volume = 0f)
        val buf2 = FloatArray(200) { 0.1f }
        e.process(buf2, 100, 2)
        assertTrue(buf2.all { it == 0f })
    }
}
