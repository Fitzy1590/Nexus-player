package com.nexus.player.ui

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolutionLabelTest {
    @Test fun scopeFilmsUseWidth() {
        assertEquals("4K", resolutionLabel(3840, 1600, null, "video/hevc"))
        assertEquals("1080p", resolutionLabel(1920, 800, null, "video/avc"))
    }
    @Test fun hdrSuffix() {
        assertEquals("4K HDR10", resolutionLabel(3840, 2160, C.COLOR_TRANSFER_ST2084, "video/hevc"))
        assertEquals("1080p HLG", resolutionLabel(1920, 1080, C.COLOR_TRANSFER_HLG, "video/hevc"))
        assertEquals("4K Dolby Vision", resolutionLabel(3840, 2160, C.COLOR_TRANSFER_ST2084, "video/dolby-vision"))
    }
    @Test fun unknownSize() = assertNull(resolutionLabel(0, 0, null, null))
}
