package com.nexus.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DoubleTapZoneTest {
    @Test fun zones() {
        assertEquals(-1, doubleTapZone(10f, 900f))
        assertEquals(0, doubleTapZone(450f, 900f))
        assertEquals(1, doubleTapZone(890f, 900f))
    }

    @Test fun zoomClampsAndSnaps() {
        assertEquals(1.5f, nextZoom(1f, 1.5f), 1e-4f)
        assertEquals(3f, nextZoom(2.5f, 2f), 1e-4f)
        assertEquals(1f, nextZoom(1.2f, 0.5f), 1e-4f)
        assertEquals(1f, snapZoom(1.03f))
        assertEquals(1.2f, snapZoom(1.2f), 1e-4f)
    }
}
