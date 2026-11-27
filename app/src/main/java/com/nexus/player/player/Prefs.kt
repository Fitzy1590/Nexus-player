package com.nexus.player.player

import android.content.Context
import com.nexus.player.audio.DspParams
import com.nexus.player.audio.Preset

/** Persisted user settings (SharedPreferences). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nexus", Context.MODE_PRIVATE)

    var seekStepSec: Int
        get() = sp.getInt("seek_step", 10)
        set(v) = sp.edit().putInt("seek_step", v).apply()
    var autoResume: Boolean
        get() = sp.getBoolean("auto_resume", false)
        set(v) = sp.edit().putBoolean("auto_resume", v).apply()
    var globalSpeed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) = sp.edit().putFloat("speed", v).apply()
    /** Forward buffer ceiling in minutes; the heap/2 byte cap still bounds memory. Applies to the next playback. */
    var bufferMin: Int
        get() = sp.getInt("buffer_min", 3)
        set(v) = sp.edit().putInt("buffer_min", v).apply()
    var afr: Boolean
        get() = sp.getBoolean("afr", true)
        set(v) = sp.edit().putBoolean("afr", v).apply()
    var diagnostics: Boolean
        get() = sp.getBoolean("diag", false)
        set(v) = sp.edit().putBoolean("diag", v).apply()

    var dsp: DspParams
        get() = DspParams(
            preset = runCatching { Preset.valueOf(sp.getString("preset", null)!!) }.getOrDefault(Preset.FLAT),
            voiceBoostDb = sp.getFloat("voice", 0f),
            eqDb = List(5) { sp.getFloat("eq$it", 0f) },
            volume = sp.getFloat("volume", 1f),
        )
        set(p) {
            val e = sp.edit().putString("preset", p.preset.name).putFloat("voice", p.voiceBoostDb)
                .putFloat("volume", p.volume)
            p.eqDb.forEachIndexed { i, v -> e.putFloat("eq$i", v) }
            e.apply()
        }

    companion object {
        val SEEK_STEPS = listOf(5, 10, 15, 30)
        /** 0.5×–3.0× in 0.1 steps. */
        val SPEEDS = (5..30).map { it / 10f }
        val BUFFER_MINUTES = listOf(3, 10, 20, 30)
    }
}
