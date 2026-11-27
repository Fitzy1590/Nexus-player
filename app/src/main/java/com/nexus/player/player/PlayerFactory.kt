package com.nexus.player.player

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexus.player.audio.DspEngine
import com.nexus.player.audio.NexusAudioProcessor
import com.nexus.player.diag.DiagLog
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
object PlayerFactory {
    /** Time ceiling; the byte cap below is the real limit for high-bitrate 4K. */
    const val FORWARD_BUFFER_MS = 180_000
    /** Covers several back-steps (10–30 s) so rewinds replay from memory instead of re-downloading. */
    const val BACK_BUFFER_MS = 30_000
    private const val MAX_TARGET_BYTES = 768L * 1024 * 1024

    /** Half the (large) heap: allocator byte[]s live in the Java heap, so this bounds OOM risk. */
    fun targetBufferBytes(heapMb: Int): Int =
        minOf(MAX_TARGET_BYTES, heapMb.toLong() * 1024 * 1024 / 2).toInt().coerceAtLeast(32 * 1024 * 1024)

    private val okHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val res = chain.proceed(req)
                DiagLog.log("http") {
                    "${res.code} ${req.url.host}${req.url.encodedPath} range=${req.header("Range")} " +
                        "type=${res.header("Content-Type")} len=${res.header("Content-Length")} " +
                        "cr=${res.header("Content-Range")} ar=${res.header("Accept-Ranges")} proto=${res.protocol}"
                }
                res
            }
            .build()
    }

    fun create(ctx: Context, dsp: DspEngine, headers: Map<String, String>): ExoPlayer {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(FORWARD_BUFFER_MS, FORWARD_BUFFER_MS, 1_000, 2_500)
            .setBackBuffer(BACK_BUFFER_MS, true)
            .setTargetBufferBytes(targetBufferBytes(am.largeMemoryClass))
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        // OkHttp: pooled keep-alive connections + HTTP/2, so seek range-requests skip TCP/TLS setup.
        val http = OkHttpDataSource.Factory(okHttp)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", true) })
            .apply { if (ua != null) setUserAgent(ua) }
        val dataSource = DefaultDataSource.Factory(ctx, http)

        val renderers = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(NexusAudioProcessor(dsp)))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }.setEnableDecoderFallback(true)

        return ExoPlayer.Builder(ctx, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
