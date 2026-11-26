package com.nexus.player

import android.content.Intent
import android.net.Uri
import com.nexus.player.intent.IntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallerHandoffTest {
    @Test fun parsesCallerStartPositionAndMarksExternal() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://h/ep1.mkv")).putExtra("position", 125_000)
        val r = IntentParser.parse(i)!!
        assertEquals(125_000L, r.startPositionMs)
        assertTrue(r.external)
    }

    @Test fun completionResultCarriesBothConventions() {
        val r = IntentParser.resultIntent(2_590_000, 2_600_000, completed = true, uri = "https://h/ep1.mkv")
        assertEquals("playback_completion", r.getStringExtra("end_by"))
        assertEquals(2_600_000, r.getIntExtra("position", -1))
        assertEquals(2_600_000, r.getIntExtra("duration", -1))
        assertEquals(2_600_000L, r.getLongExtra("extra_position", -1))
        assertEquals(2_600_000L, r.getLongExtra("extra_duration", -1))
    }

    @Test fun userExitReportsCurrentPosition() {
        val r = IntentParser.resultIntent(600_000, 2_600_000, completed = false, uri = "https://h/ep1.mkv")
        assertEquals("user", r.getStringExtra("end_by"))
        assertEquals(600_000, r.getIntExtra("position", -1))
    }
}
