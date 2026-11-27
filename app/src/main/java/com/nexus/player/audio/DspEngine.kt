package com.nexus.player.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Preset { FLAT, DIALOGUE, VOCAL_ISOLATION, NIGHT, CUSTOM }

/** Immutable; built on the UI thread and swapped atomically into [DspEngine.params]. */
data class DspParams(
    val preset: Preset = Preset.FLAT,
    /** Live Voice Boost, 0..12 dB, added on top of the preset's dialogue gain. */
    val voiceBoostDb: Float = 0f,
    /** Custom 5-band EQ gains (-12..12 dB) at [EQ_FREQS]. Used only by [Preset.CUSTOM]. */
    val eqDb: List<Float> = List(5) { 0f },
    /** Software volume 0..2 (0–200 %). */
    val volume: Float = 1f,
) {
    companion object {
        val EQ_FREQS = floatArrayOf(60f, 250f, 1000f, 4000f, 12000f)
    }
}

/**
 * Interleaved float PCM chain:
 * mid/side (vocal isolation) → low-shelf bass preserve → dialogue peak (1–4 kHz, centre channel
 * when ≥3 ch) → 5-band EQ → DRC compressor → software gain → peak limiter (-1 dBFS).
 * No allocation in [process].
 */
class DspEngine {
    @Volatile var params: DspParams = DspParams()

    private var applied: DspParams? = null
    private var sampleRate = 48_000
    private var channels = 2

    // Filter slots: 0 = bass shelf, 1 = dialogue peak, 2..6 = EQ bands.
    private val nFilters = 7
    private val coef = Array(nFilters) { FloatArray(5) } // b0 b1 b2 a1 a2 (a0-normalised)
    private val enabled = BooleanArray(nFilters)
    private val state = Array(nFilters) { FloatArray(MAX_CH * 4) } // x1 x2 y1 y2 per channel

    private var sideGain = 1f
    private var compThrDb = 0f
    private var compRatio = 1f
    private var compMakeupDb = 0f
    private var compEnvDb = -120f
    private var compAtk = 0f
    private var compRel = 0f
    private var limGain = 1f
    private var limRel = 0f

    fun configure(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels.coerceIn(1, MAX_CH)
        compAtk = exp(-1f / (0.010f * sampleRate))
        compRel = exp(-1f / (0.150f * sampleRate))
        limRel = exp(-1f / (0.050f * sampleRate))
        applied = null
        reset()
    }

    fun reset() {
        state.forEach { it.fill(0f) }
        compEnvDb = -120f
        limGain = 1f
    }

    fun process(buf: FloatArray, frames: Int, ch: Int) {
        val p = params
        if (p !== applied) rebuild(p)
        val volume = p.volume.coerceIn(0f, 2f)
        val dialogueOnCentre = ch >= 3
        var i = 0
        for (f in 0 until frames) {
            if (ch == 2 && sideGain != 1f) {
                val l = buf[i]; val r = buf[i + 1]
                val m = (l + r) * 0.5f; val s = (l - r) * 0.5f * sideGain
                buf[i] = m + s; buf[i + 1] = m - s
            }
            var peak = 0f
            for (c in 0 until ch) {
                var x = buf[i + c]
                for (k in 0 until nFilters) {
                    if (!enabled[k]) continue
                    if (k == 1 && dialogueOnCentre && c != 2) continue
                    x = biquad(k, c, x)
                }
                buf[i + c] = x
                peak = max(peak, abs(x))
            }
            // DRC (feed-forward, linked across channels).
            var g = volume
            if (compRatio > 1f) {
                val inDb = if (peak > 1e-6f) 20f * log10(peak) else -120f
                val coeff = if (inDb > compEnvDb) compAtk else compRel
                compEnvDb = inDb + coeff * (compEnvDb - inDb)
                val over = compEnvDb - compThrDb
                val grDb = if (over > 0f) over * (1f - 1f / compRatio) else 0f
                g *= dbToLin(compMakeupDb - grDb)
            }
            // Limiter: instant attack, smooth release; guarantees ≤ CEILING.
            val out = peak * g
            val needed = if (out > CEILING) CEILING / out else 1f
            limGain = if (needed < limGain) needed else needed + limRel * (limGain - needed)
            g *= limGain
            for (c in 0 until ch) buf[i + c] = (buf[i + c] * g).coerceIn(-CEILING, CEILING)
            i += ch
        }
    }

    private fun biquad(k: Int, c: Int, x: Float): Float {
        val cf = coef[k]; val s = state[k]; val o = c * 4
        val y = cf[0] * x + cf[1] * s[o] + cf[2] * s[o + 1] - cf[3] * s[o + 2] - cf[4] * s[o + 3]
        s[o + 1] = s[o]; s[o] = x; s[o + 3] = s[o + 2]; s[o + 2] = y
        return y
    }

    private fun rebuild(p: DspParams) {
        val presetDialogue: Float
        val bass: Float
        when (p.preset) {
            Preset.FLAT -> { presetDialogue = 0f; bass = 0f; sideGain = 1f; setComp(0f, 1f, 0f) }
            Preset.DIALOGUE -> { presetDialogue = 6f; bass = 2f; sideGain = 1f; setComp(-24f, 3f, 3f) }
            Preset.VOCAL_ISOLATION -> { presetDialogue = 4f; bass = -4f; sideGain = 0.25f; setComp(-24f, 2f, 2f) }
            Preset.NIGHT -> { presetDialogue = 3f; bass = 0f; sideGain = 1f; setComp(-32f, 4f, 8f) }
            Preset.CUSTOM -> { presetDialogue = 0f; bass = 0f; sideGain = 1f; setComp(0f, 1f, 0f) }
        }
        val dialogue = (presetDialogue + p.voiceBoostDb.coerceIn(0f, 12f)).coerceAtMost(18f)
        // Bass preservation: shelf below the vocal band compensates perceived thinning from dialogue lift.
        val shelf = (bass + dialogue / 3f).coerceIn(-12f, 6f)
        setFilter(0, shelf != 0f) { lowShelf(it, 120f, shelf) }
        setFilter(1, dialogue != 0f) { peaking(it, 2000f, 0.67f, dialogue) }
        for (b in 0 until 5) {
            val db = if (p.preset == Preset.CUSTOM) p.eqDb.getOrElse(b) { 0f }.coerceIn(-12f, 12f) else 0f
            setFilter(2 + b, db != 0f) { peaking(it, DspParams.EQ_FREQS[b], 1.0f, db) }
        }
        applied = p
    }

    private inline fun setFilter(k: Int, on: Boolean, design: (FloatArray) -> Unit) {
        if (on && !enabled[k]) state[k].fill(0f)
        enabled[k] = on
        if (on) design(coef[k])
    }

    private fun setComp(thr: Float, ratio: Float, makeup: Float) {
        compThrDb = thr; compRatio = ratio; compMakeupDb = makeup
    }

    // RBJ Audio-EQ-Cookbook designs.
    private fun peaking(out: FloatArray, f0: Float, q: Float, db: Float) {
        val f = f0.coerceAtMost(sampleRate * 0.45f)
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f / sampleRate
        val alpha = sin(w) / (2 * q)
        norm(out, 1 + alpha * a, -2 * cos(w), 1 - alpha * a, 1 + alpha / a, -2 * cos(w), 1 - alpha / a)
    }

    private fun lowShelf(out: FloatArray, f0: Float, db: Float) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f0 / sampleRate
        val cw = cos(w)
        val alpha = sin(w) / 2 * sqrt(2.0) // S = 1
        val sa = 2 * sqrt(a) * alpha
        norm(
            out,
            a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa,
        )
    }

    private fun norm(out: FloatArray, b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        out[0] = (b0 / a0).toFloat(); out[1] = (b1 / a0).toFloat(); out[2] = (b2 / a0).toFloat()
        out[3] = (a1 / a0).toFloat(); out[4] = (a2 / a0).toFloat()
    }

    companion object {
        const val MAX_CH = 8
        const val CEILING = 0.891f // -1 dBFS
        fun dbToLin(db: Float): Float = 10f.pow(db / 20f)
    }
}
