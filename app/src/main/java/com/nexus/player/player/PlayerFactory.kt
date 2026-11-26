package com.nexus.player.player

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nexus.player.audio.DspEngine
import com.nexus.player.audio.NexusAudioProcessor

@UnstableApi
object PlayerFactory {
    const val FORWARD_BUFFER_MS = 60_000
    const val BACK_BUFFER_MS = 5_000
    private const val MAX_TARGET_BYTES = 400L * 1024 * 1024

    /** Heap-bounded byte cap: 60 s of ~80 Mbps 4K ≈ 600 MB would exceed most TV heaps. */
    fun targetBufferBytes(heapMb: Int): Int =
        minOf(MAX_TARGET_BYTES, heapMb.toLong() * 1024 * 1024 / 3).toInt().coerceAtLeast(32 * 1024 * 1024)

    fun create(ctx: Context, dsp: DspEngine, headers: Map<String, String>): ExoPlayer {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, FORWARD_BUFFER_MS, 1_500, 3_000)
            .setBackBuffer(BACK_BUFFER_MS, true)
            .setTargetBufferBytes(targetBufferBytes(am.largeMemoryClass))
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
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
