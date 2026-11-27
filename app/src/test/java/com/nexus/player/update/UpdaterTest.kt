package com.nexus.player.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) // real org.json
@Config(sdk = [34])
class UpdaterTest {
    @Test fun versionCompare() {
        assertTrue(Updater.isNewer("0.2.0", "0.1.9"))
        assertTrue(Updater.isNewer("1.0.0", "0.10.0"))
        assertTrue(Updater.isNewer("0.10.0", "0.9.0")) // numeric, not string, compare
        assertFalse(Updater.isNewer("0.2.0", "0.2.0"))
        assertFalse(Updater.isNewer("0.1.0", "0.2.0"))
        assertFalse(Updater.isNewer("garbage", "0.1.0"))
    }

    @Test fun parsesLatestRelease() {
        val json = """{"tag_name":"v0.3.1","assets":[
            {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
            {"name":"nexus-player-0.3.1.apk","browser_download_url":"https://github.com/o/r/releases/download/v0.3.1/nexus-player-0.3.1.apk",
             "size":123,"digest":"sha256:ABCDEF"}]}"""
        val r = Updater.parseLatest(json)!!
        assertEquals("0.3.1", r.version)
        assertEquals("https://github.com/o/r/releases/download/v0.3.1/nexus-player-0.3.1.apk", r.apkUrl)
        assertEquals("ABCDEF", r.sha256)
        assertEquals(123L, r.sizeBytes)
    }

    @Test fun rejectsBadTagOrNoApk() {
        assertNull(Updater.parseLatest("""{"tag_name":"nightly","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""))
        assertNull(Updater.parseLatest("""{"tag_name":"v1.0.0","assets":[{"name":"a.zip","browser_download_url":"https://x/a.zip"}]}"""))
    }

    @Test fun missingDigestIsNull() {
        val r = Updater.parseLatest("""{"tag_name":"v1.0.0","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}""")!!
        assertNull(r.sha256)
    }
}
