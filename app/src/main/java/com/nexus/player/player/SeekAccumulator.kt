package com.nexus.player.player

/**
 * Coalesces rapid seek inputs (D-pad, double-taps, scrub) into one pending target,
 * so the player performs a single seek after input settles instead of one per press.
 */
class SeekAccumulator {
    var target: Long? = null
        private set

    /** Adds [deltaMs] to the pending target (or [currentMs] if none). Returns the new target. */
    fun nudge(currentMs: Long, durationMs: Long, deltaMs: Long): Long =
        set((target ?: currentMs) + deltaMs, durationMs)

    /** Absolute target (timeline scrub). */
    fun set(ms: Long, durationMs: Long): Long {
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return ms.coerceIn(0L, max).also { target = it }
    }

    /** Returns and clears the pending target. */
    fun take(): Long? = target.also { target = null }

    fun cancel() { target = null }

    companion object {
        const val SETTLE_MS = 80L

        /** Hold-to-accelerate: key auto-repeat count → step multiplier. */
        fun multiplier(repeatCount: Int): Int = when {
            repeatCount < 4 -> 1
            repeatCount < 12 -> 3
            else -> 6
        }
    }
}
