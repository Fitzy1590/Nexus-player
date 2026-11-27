package com.nexus.player

import com.nexus.player.player.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedStepsTest {
    @Test fun fineStepsTo1_3ThenTenths() {
        val s = Prefs.SPEEDS
        assertEquals(0.5f, s.first(), 0f)
        assertEquals(3.0f, s.last(), 0f)
        assertTrue(s.containsAll(listOf(1.0f, 1.05f, 1.25f, 1.3f, 1.4f, 2.0f)))
        assertTrue(1.35f !in s && 1.31f !in s)
        assertEquals(s.sorted(), s)
        assertEquals(s.size, s.toSet().size)
    }
}
