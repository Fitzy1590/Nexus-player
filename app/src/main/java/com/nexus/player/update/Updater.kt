package com.nexus.player.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nexus.player.diag.DiagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A published release: version from the `vX.Y.Z` tag, first `.apk` asset, optional GitHub sha256 digest. */
data class Release(val version: String, val apkUrl: String, val sha256: String?, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from a PUBLIC releases-only GitHub repo (source repo stays private; no token in the APK).
 * Android itself refuses any APK not signed with the installed app's key, so a tampered download cannot install.
 */
object Updater {
    const val RELEASES_REPO = "Fitzy1590/nexus-player-releases"
    private const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        internal set

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun installedVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** [force] = user tapped "Check"; otherwise throttled to once per 12 h. */
    suspend fun check(ctx: Context, force: Boolean) {
        val sp = ctx.getSharedPreferences("nexus", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("update_checked_at", 0L) < AUTO_CHECK_INTERVAL_MS) return
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return
        state = UpdateState.Checking
        state = runCatching {
            val body = withContext(Dispatchers.IO) {
                val req = Request.Builder().url("https://api.github.com/repos/$RELEASES_REPO/releases/latest")
                    .header("Accept", "application/vnd.github+json").build()
                http.newCall(req).execute().use { r ->
                    if (r.code == 404) return@withContext null // no release published yet
                    check(r.isSuccessful) { "GitHub returned HTTP ${r.code}" }
                    r.body?.string()
                }
            }
            sp.edit().putLong("update_checked_at", now).apply()
            val rel = body?.let(::parseLatest)
            if (rel != null && isNewer(rel.version, installedVersion(ctx))) UpdateState.Available(rel) else UpdateState.UpToDate
        }.getOrElse { e ->
            DiagLog.log("update") { "check failed: ${e.message}" }
            if (force) UpdateState.Failed("Couldn't check for updates: ${e.message}") else UpdateState.Idle
        }
    }

    /** Downloads the APK into cache, verifies its digest when GitHub provides one, then hands it to PackageInstaller. */
    suspend fun install(ctx: Context, rel: Release) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.Failed("Allow Nexus Player to install apps, then tap Install again.")
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
            } catch (_: ActivityNotFoundException) {
                state = UpdateState.Failed("Turn on \"Install unknown apps\" for Nexus Player in system settings, then tap Install again.")
            }
            return
        }
        state = UpdateState.Downloading(0)
        state = runCatching {
            val apk = withContext(Dispatchers.IO) { download(app, rel) }
            withContext(Dispatchers.IO) { commitSession(app, apk) }
            UpdateState.Installing
        }.getOrElse { e ->
            DiagLog.log("update") { "install failed: ${e.message}" }
            UpdateState.Failed("Update failed: ${e.message}")
        }
    }

    private fun download(ctx: Context, rel: Release): File {
        require(rel.apkUrl.startsWith("https://")) { "refusing non-HTTPS download" }
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "nexus-player-${rel.version}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { r ->
            check(r.isSuccessful) { "download returned HTTP ${r.code}" }
            val body = checkNotNull(r.body) { "empty download" }
            val total = body.contentLength().takeIf { it > 0 } ?: rel.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; state = UpdateState.Downloading(pct) }
                        }
                    }
                }
            }
        }
        rel.sha256?.let { expected ->
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(expected, ignoreCase = true)) { "checksum mismatch, download discarded" }
        }
        return out
    }

    private fun commitSession(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
            s.commit(pending.intentSender)
        }
    }

    /** Parses `GET /releases/latest`. Null when the tag isn't `vX.Y.Z` or no `.apk` asset exists. */
    fun parseLatest(json: String): Release? {
        val o = JSONObject(json)
        val version = o.optString("tag_name").removePrefix("v").takeIf { SEMVER.matches(it) } ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (!a.optString("name").endsWith(".apk")) continue
            val digest = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            return Release(version, a.getString("browser_download_url"), digest, a.optLong("size"))
        }
        return null
    }

    /** True when [remote] is a strictly higher X.Y.Z than [local]; unparseable versions never trigger an update. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote) ?: return false
        val l = parts(local) ?: return true
        for (i in 0..2) if (r[i] != l[i]) return r[i] > l[i]
        return false
    }

    private val SEMVER = Regex("""\d+\.\d+\.\d+""")
    private fun parts(v: String) = v.takeIf { SEMVER.matches(it) }?.split('.')?.map { it.toInt() }
}

/** Receives PackageInstaller results; launches the system confirm dialog when the OS asks for it. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.state = UpdateState.Idle // process is replaced right after
            else -> Updater.state = UpdateState.Failed(
                "Install failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"}",
            )
        }
    }
}
