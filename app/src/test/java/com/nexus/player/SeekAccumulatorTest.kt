package com.nexus.player

import com.nexus.player.player.SeekAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekAccumulatorTest {
    @Test fun rapidPressesCoalesceIntoOneTarget() {
        val a = SeekAccumulator()
        repeat(3) { a.nudge(currentMs = 100_000, durationMs = 600_000, deltaMs = 10_000) }
        assertEquals(130_000L, a.take())
        assertNull(a.take())
    }

    @Test fun clampsToBounds() {
        val a = SeekAccumulator()
        assertEquals(0L, a.nudge(5_000, 600_000, -10_000))
        a.cancel()
        assertEquals(600_000L, a.nudge(595_000, 600_000, 10_000))
    }

    @Test fun unknownDurationIsUnbounded() {
        assertEquals(1_010_000L, SeekAccumulator().nudge(1_000_000, 0, 10_000))
    }

    @Test fun holdAccelerates() {
        assertEquals(1, SeekAccumulator.multiplier(0))
        assertEquals(3, SeekAccumulator.multiplier(5))
        assertEquals(6, SeekAccumulator.multiplier(20))
    }
}
