package com.nexus.player.afr

import kotlin.math.abs
import kotlin.math.roundToInt

data class Mode(val id: Int, val width: Int, val height: Int, val refresh: Float)

object FrameRateMatcher {
    /**
     * Picks a mode at the current resolution whose refresh is an integer multiple of [fps]
     * (exact match preferred, then lowest multiple). Returns null if [fps] is unknown or nothing fits.
     * 23.976 vs 24 Hz are treated as distinct (0.1 % tolerance).
     */
    fun pick(modes: List<Mode>, current: Mode, fps: Float): Mode? {
        if (fps <= 0f || fps.isNaN()) return null
        return modes.asSequence()
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { m ->
                val k = (m.refresh / fps).roundToInt()
                if (k < 1 || abs(m.refresh - k * fps) > 0.001f * m.refresh) null else k to m
            }
            .minByOrNull { (k, m) -> k * 1000 + if (m.id == current.id) 0 else 1 }
            ?.second
    }
}
