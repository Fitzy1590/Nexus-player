package com.nexus.player

import androidx.media3.common.util.UnstableApi
import com.nexus.player.player.PlaybackController
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class HdrLabelTest {
    @Test fun sdrWhenNoTypes() {
        assertEquals("SDR only", PlaybackController.hdrLabel(null))
        assertEquals("SDR only", PlaybackController.hdrLabel(intArrayOf()))
    }

    @Test fun listsSupportedTypesInOrder() {
        assertEquals("DV · HDR10 · HLG", PlaybackController.hdrLabel(intArrayOf(3, 1, 2)))
    }
}
