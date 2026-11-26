package com.nexus.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapZoneTest {
    @Test fun zones() {
        assertEquals(-1, doubleTapZone(10f, 900f))
        assertEquals(0, doubleTapZone(450f, 900f))
        assertEquals(1, doubleTapZone(890f, 900f))
    }
}
