package com.nexus.player.diag

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Toggle-controlled ring buffer. When [enabled] is false, [log] is a single field read. */
object DiagLog {
    @Volatile var enabled = false
        set(v) { field = v; if (!v) synchronized(lines) { lines.clear() } }

    private const val MAX = 2000
    private val lines = ArrayDeque<String>(MAX)
    private val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, msg: () -> String) {
        if (!enabled) return
        val line = "${ts.format(Date())} [$tag] ${Redactor.redact(msg())}"
        synchronized(lines) { if (lines.size == MAX) lines.removeFirst(); lines.addLast(line) }
    }

    /** Writes one sanitised .txt and returns a share intent. [state] is redacted again on export. */
    fun export(ctx: Context, state: String): Intent {
        val dir = File(ctx.cacheDir, "diag").apply { mkdirs() }
        val f = File(dir, "nexus-diag-${System.currentTimeMillis()}.txt")
        val body = buildString {
            appendLine("# Nexus Player diagnostic package")
            appendLine("app=${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
            appendLine("logging=${if (enabled) "on" else "off"}")
            appendLine("\n## State"); appendLine(Redactor.redact(state))
            appendLine("\n## Log"); synchronized(lines) { lines.forEach(::appendLine) }
        }
        f.writeText(body)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.diag", f)
        return Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
