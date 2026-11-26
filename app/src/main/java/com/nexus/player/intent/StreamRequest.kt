package com.nexus.player.intent

import android.content.Intent
import android.os.Bundle

/** Parsed VIEW intent. [headers] go to the HTTP data source only; never log raw values. */
data class StreamRequest(
    val uri: String,
    val mimeType: String?,
    val title: String?,
    val headers: Map<String, String>,
    /** Start position supplied by the caller (MX/VLC `position` extra); 0 = none. */
    val startPositionMs: Long = 0,
    /** True when launched by another app via VIEW (result is returned on exit). */
    val external: Boolean = false,
)

object IntentParser {
    val ALLOWED_SCHEMES = setOf("http", "https", "content", "file")

    fun isAllowed(scheme: String?): Boolean = scheme?.lowercase() in ALLOWED_SCHEMES

    /** MX Player convention: String[] of alternating key, value. Odd trailing key is dropped. */
    fun pairsToHeaders(arr: Array<String>?): Map<String, String> {
        if (arr == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i + 1 < arr.size) {
            val k = arr[i].trim(); val v = arr[i + 1]
            if (isSafeHeader(k, v)) out[k] = v
            i += 2
        }
        return out
    }

    /** Rejects empty names and CR/LF (header injection). */
    fun isSafeHeader(name: String, value: String): Boolean =
        name.isNotEmpty() && name.none { it == '\r' || it == '\n' || it == ':' } &&
            value.none { it == '\r' || it == '\n' }

    fun parse(intent: Intent?): StreamRequest? {
        val data = intent?.data ?: return null
        if (!isAllowed(data.scheme)) return null
        val headers = LinkedHashMap<String, String>()
        headers += pairsToHeaders(intent.getStringArrayExtra("headers"))
        intent.getBundleExtra(EXTRA_BROWSER_HEADERS)?.let { headers += bundleHeaders(it) }
        return StreamRequest(
            uri = data.toString(),
            mimeType = intent.type,
            title = intent.getStringExtra("title") ?: intent.getStringExtra(Intent.EXTRA_TITLE),
            headers = headers,
            startPositionMs = startPosition(intent),
            external = true,
        )
    }

    /** MX Player sends `position` as Int ms; VLC uses `extra_position` (Long ms). */
    private fun startPosition(i: Intent): Long {
        val mx = i.getIntExtra("position", 0).toLong()
        val vlc = i.getLongExtra("extra_position", 0L)
        return maxOf(mx, vlc).coerceAtLeast(0)
    }

    /**
     * Result for the launching app (Stremio/Nuvio/etc.). Both common conventions are filled:
     * MX Player (`position`/`duration` Int ms, `end_by`) and VLC (`extra_position`/`extra_duration` Long ms).
     */
    fun resultIntent(positionMs: Long, durationMs: Long, completed: Boolean, uri: String): Intent =
        Intent().setData(android.net.Uri.parse(uri))
            .putExtra("position", (if (completed) durationMs else positionMs).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            .putExtra("duration", durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            .putExtra("end_by", if (completed) "playback_completion" else "user")
            .putExtra("extra_position", if (completed) durationMs else positionMs)
            .putExtra("extra_duration", durationMs)

    private fun bundleHeaders(b: Bundle): Map<String, String> =
        b.keySet().mapNotNull { k -> b.getString(k)?.let { v -> k to v } }
            .filter { (k, v) -> isSafeHeader(k, v) }.toMap()

    /** android.provider.Browser.EXTRA_HEADERS */
    private const val EXTRA_BROWSER_HEADERS = "com.android.browser.headers"
}
