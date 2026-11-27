package com.nexus.player

import com.nexus.player.afr.FrameRateMatcher
import com.nexus.player.afr.Mode
import com.nexus.player.data.NexusDb
import com.nexus.player.data.PlaybackState
import com.nexus.player.diag.Redactor
import com.nexus.player.intent.IntentParser
import com.nexus.player.player.PlayerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {
    @Test fun headerPairs() {
        val h = IntentParser.pairsToHeaders(arrayOf("Referer", "https://a", "User-Agent", "X/1", "dangling"))
        assertEquals(mapOf("Referer" to "https://a", "User-Agent" to "X/1"), h)
    }

    @Test fun headerInjectionRejected() {
        val h = IntentParser.pairsToHeaders(arrayOf("X-A", "v\r\nHost: evil", "Bad:Name", "v", "", "v"))
        assertTrue(h.isEmpty())
    }

    @Test fun schemeAllowlist() {
        listOf("http", "HTTPS", "content", "file").forEach { assertTrue(IntentParser.isAllowed(it)) }
        listOf("javascript", "magnet", "intent", null).forEach { assertFalse(IntentParser.isAllowed(it)) }
    }

    @Test fun redaction() {
        val raw = "Authorization: Bearer abc.def GET https://u:p@h/x.m3u8?token=s3cr3t&q=1 Cookie: SWID={X}; espn_s2=AAA"
        val r = Redactor.redact(raw)
        listOf("abc.def", "s3cr3t", "u:p", "{X}", "AAA").forEach { assertFalse("leak $it in $r", it in r) }
        assertTrue("q=1" in r)
    }

    @Test fun redactsOpaquePathSegments() {
        val seg = "eyJhcGlLZXkiOiJTRUNSRVQtVE9LRU4tMTIzNDU2Nzg5MCJ9"
        val r = Redactor.redact("uri=https://h.example/$seg/playback/abc/0?name=x")
        assertFalse(seg in r)
        assertTrue("/playback/abc/0?name=x" in r)
    }

    private val m60 = Mode(1, 3840, 2160, 60f)
    private val modes = listOf(
        m60, Mode(2, 3840, 2160, 23.976f), Mode(3, 3840, 2160, 24f), Mode(4, 3840, 2160, 50f),
        Mode(5, 3840, 2160, 119.88f), Mode(6, 1920, 1080, 23.976f),
    )

    @Test fun afrExactMatch() {
        assertEquals(2, FrameRateMatcher.pick(modes, m60, 23.976f)?.id)
        assertEquals(3, FrameRateMatcher.pick(modes, m60, 24f)?.id)
        assertEquals(4, FrameRateMatcher.pick(modes, m60, 25f)?.id)
        assertEquals(1, FrameRateMatcher.pick(modes, m60, 30f)?.id)
        assertEquals(5, FrameRateMatcher.pick(modes, m60, 59.94f)?.id)
    }

    @Test fun afrNoMatchOrUnknown() {
        assertNull(FrameRateMatcher.pick(listOf(m60), m60, 23.976f))
        assertNull(FrameRateMatcher.pick(modes, m60, -1f))
    }

    @Test fun resumeKeyStripsVolatileParams() {
        assertEquals("https://h/v.mkv?id=7", NexusDb.keyFor("https://h/v.mkv?token=a&id=7&expires=9#t=1"))
        assertEquals("https://h/v.mkv", NexusDb.keyFor("https://h/v.mkv?sig=zz"))
    }

    @Test fun resumeThreshold() {
        fun s(pos: Long, dur: Long, done: Boolean = false) = PlaybackState("k", pos, dur, done, null, null, null, 0)
        assertFalse(NexusDb.shouldResume(s(3_000, 100_000)))
        assertTrue(NexusDb.shouldResume(s(30_000, 100_000)))
        assertFalse(NexusDb.shouldResume(s(96_000, 100_000)))
        assertFalse(NexusDb.shouldResume(s(30_000, 100_000, done = true)))
    }

    /** 512 MB large-heap TV box must hold ≥25 s of 80 Mbps 4K (old heap/3 cap gave ~17 s). */
    @Test fun bufferHoldsEnough4kOnTypicalTvHeap() {
        val seconds = PlayerFactory.targetBufferBytes(512) * 8.0 / 80_000_000
        assertTrue("seconds=$seconds", seconds >= 25)
    }

    @Test fun bufferBytesBoundedByHeap() {
        assertEquals(256 * 1024 * 1024, PlayerFactory.targetBufferBytes(512))
        assertEquals(768 * 1024 * 1024, PlayerFactory.targetBufferBytes(4096))
        assertEquals(32 * 1024 * 1024, PlayerFactory.targetBufferBytes(64))
    }
}
