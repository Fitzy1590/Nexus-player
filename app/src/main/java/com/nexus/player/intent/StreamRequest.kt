package com.nexus.player.intent

import android.content.Intent
import android.os.Bundle

/** Parsed VIEW intent. [headers] go to the HTTP data source only; never log raw values. */
data class StreamRequest(
    val uri: String,
    val mimeType: String?,
    val title: String?,
    val headers: Map<String, String>,
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
        )
    }

    private fun bundleHeaders(b: Bundle): Map<String, String> =
        b.keySet().mapNotNull { k -> b.getString(k)?.let { v -> k to v } }
            .filter { (k, v) -> isSafeHeader(k, v) }.toMap()

    /** android.provider.Browser.EXTRA_HEADERS */
    private const val EXTRA_BROWSER_HEADERS = "com.android.browser.headers"
}
