package com.nexus.player.player

import android.app.Activity
import android.os.Build
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import com.nexus.player.afr.FrameRateMatcher
import com.nexus.player.afr.Mode
import com.nexus.player.audio.DspEngine
import com.nexus.player.audio.DspParams
import com.nexus.player.data.NexusDb
import com.nexus.player.data.PlaybackState
import com.nexus.player.diag.DiagLog
import com.nexus.player.intent.StreamRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class PlaybackController(
    private val activity: Activity,
    val request: StreamRequest,
    /** Called once when the item plays to the end. */
    private val onCompleted: () -> Unit = {},
) {
    val prefs = Prefs(activity)
    private val dsp = DspEngine().apply { params = prefs.dsp }
    val player: ExoPlayer = PlayerFactory.create(activity, dsp, request.headers)
    private val dao = NexusDb.get(activity).playbackState()
    private val key = NexusDb.keyFor(request.uri)
    private var saved: PlaybackState? = null
    private var tracksRestored = false

    var dspParams by mutableStateOf(dsp.params); private set
    var passthrough by mutableStateOf(false); private set
    var resumePrompt by mutableStateOf<PlaybackState?>(null); private set
    var speed by mutableFloatStateOf(prefs.globalSpeed); private set
    var perItemSpeed by mutableStateOf(false); private set
    var decoderName by mutableStateOf("—"); private set
    var bandwidthBps by mutableLongStateOf(0L); private set
    var displayHz by mutableFloatStateOf(0f); private set
    var error by mutableStateOf<String?>(null); private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) save() }
        override fun onPlaybackStateChanged(state: Int) {
            DiagLog.log("player") { "state=$state" }
            if (state == Player.STATE_ENDED) { save(completed = true); onCompleted() }
        }
        override fun onTracksChanged(tracks: Tracks) { if (!tracksRestored && !tracks.isEmpty) restoreTracks(tracks) }
        override fun onPlayerError(e: PlaybackException) {
            DiagLog.log("player") { "error ${e.errorCodeName}: ${e.message} cause=${e.cause}" }
            error = "${e.errorCodeName}\n${e.message ?: ""}"
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onAudioTrackInitialized(t: AnalyticsListener.EventTime, c: AudioSink.AudioTrackConfig) {
            passthrough = !Util.isEncodingLinearPcm(c.encoding)
            DiagLog.log("audio") { "track enc=${c.encoding} sr=${c.sampleRate} passthrough=$passthrough" }
            applyVolume()
        }
        override fun onVideoDecoderInitialized(t: AnalyticsListener.EventTime, name: String, initMs: Long, durMs: Long) {
            decoderName = name
            DiagLog.log("video") { "decoder=$name" }
        }
        override fun onVideoInputFormatChanged(t: AnalyticsListener.EventTime, f: Format, e: DecoderReuseEvaluation?) {
            DiagLog.log("video") { "format ${f.width}x${f.height} ${f.sampleMimeType} fps=${f.frameRate}" }
            if (prefs.afr) applyAfr(f.frameRate)
        }
        override fun onBandwidthEstimate(t: AnalyticsListener.EventTime, loadMs: Int, bytes: Long, bitrate: Long) {
            bandwidthBps = bitrate
        }
    }

    // Periodic progress save so a crash / force-stop / power-off loses at most 10 s.
    private val autosave = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        while (true) {
            delay(AUTOSAVE_MS)
            if (player.isPlaying) save()
        }
    }

    fun start() {
        DiagLog.log("intent") { "uri=${request.uri} mime=${request.mimeType} headers=${request.headers.keys}" }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        player.setMediaItem(
            MediaItem.Builder().setUri(request.uri).setMimeType(mimeHint(request.mimeType))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title).build()).build(),
        )
        applyVolume()
        displayHz = currentDisplay()?.mode?.refreshRate ?: 0f
        ioScope.launch {
            val s = dao.get(key)
            withContext(Dispatchers.Main) {
                saved = s
                s?.speed?.let { speed = it; perItemSpeed = true }
                player.setPlaybackSpeed(speed)
                val resume = NexusDb.shouldResume(s)
                if (request.startPositionMs > 0) player.seekTo(request.startPositionMs) // caller's progress wins
                else if (resume && prefs.autoResume) player.seekTo(s!!.positionMs)
                else if (resume) resumePrompt = s
                player.prepare()
                player.playWhenReady = resumePrompt == null
            }
        }
    }

    fun retry() {
        error = null
        player.prepare()
        player.play()
    }

    fun answerResume(resume: Boolean) {
        resumePrompt?.let { if (resume) player.seekTo(it.positionMs) }
        resumePrompt = null
        player.play()
    }

    fun updateDsp(p: DspParams) {
        dspParams = p
        dsp.params = p // atomic swap; audio thread rebuilds coefficients on next buffer
        prefs.dsp = p
        applyVolume()
    }

    /** ±delta in 0..2 (0–200 %). */
    fun nudgeVolume(delta: Float) =
        updateDsp(dspParams.copy(volume = ((dspParams.volume + delta) * 100).let { Math.round(it) / 100f }.coerceIn(0f, 2f)))

    /** DSP gain is bypassed in passthrough, so fall back to player volume (≤100 %). */
    private fun applyVolume() {
        player.volume = if (passthrough) dspParams.volume.coerceAtMost(1f) else 1f
    }

    val stepMs: Long get() = prefs.seekStepSec * 1000L

    val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    fun seekBy(direction: Int) = seekTo(player.currentPosition + stepMs * direction)

    /** Short hops decode exactly; long jumps snap to the nearest keyframe so 4K seeks land instantly. */
    fun seekTo(targetMs: Long) {
        val max = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        val t = targetMs.coerceIn(0L, max)
        val far = kotlin.math.abs(t - player.currentPosition) >= LONG_JUMP_MS
        player.setSeekParameters(if (far) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
        DiagLog.log("seek") { "to=$t far=$far" }
        player.seekTo(t)
    }

    fun setSpeed(s: Float, thisItemOnly: Boolean) {
        speed = s
        perItemSpeed = thisItemOnly
        player.setPlaybackSpeed(s)
        if (!thisItemOnly) prefs.globalSpeed = s
        save()
    }

    fun selectTrack(type: Int, group: Tracks.Group?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (group == null) b.setTrackTypeDisabled(type, true)
        else b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        player.trackSelectionParameters = b.build()
        save()
    }

    private fun selectedId(type: Int): String? = player.currentTracks.groups
        .firstOrNull { it.type == type && it.isSelected }
        ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).id } }

    private fun restoreTracks(tracks: Tracks) {
        tracksRestored = true
        val s = saved ?: return
        val b = player.trackSelectionParameters.buildUpon()
        fun find(type: Int, id: String?) = id?.let {
            tracks.groups.firstOrNull { g -> g.type == type && (0 until g.length).any { i -> g.getTrackFormat(i).id == id } }
        }
        find(C.TRACK_TYPE_AUDIO, s.audioTrackId)?.let { b.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0)) }
        find(C.TRACK_TYPE_TEXT, s.subtitleTrackId)?.let { b.setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0)) }
        player.trackSelectionParameters = b.build()
    }

    fun save(completed: Boolean = false) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val pos = player.currentPosition
        if (pos <= 0 && !completed) return
        val state = PlaybackState(
            mediaKey = key, positionMs = if (completed) 0 else pos, durationMs = dur,
            completed = completed || (dur > 0 && pos >= dur * 0.95),
            audioTrackId = selectedId(C.TRACK_TYPE_AUDIO), subtitleTrackId = selectedId(C.TRACK_TYPE_TEXT),
            speed = if (perItemSpeed) speed else null, updatedAt = System.currentTimeMillis(),
        )
        ioScope.launch { dao.upsert(state) }
    }

    private fun currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= 30) activity.display else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay

    /** Mode switch only; Media3 already calls Surface.setFrameRate for seamless changes. */
    private fun applyAfr(fps: Float) {
        if (fps == Format.NO_VALUE.toFloat()) return
        val d = currentDisplay() ?: return
        val cur = d.mode
        fun m(x: Display.Mode) = Mode(x.modeId, x.physicalWidth, x.physicalHeight, x.refreshRate)
        val pick = FrameRateMatcher.pick(d.supportedModes.map(::m), m(cur), fps) ?: return
        DiagLog.log("afr") { "fps=$fps current=${cur.refreshRate} -> ${pick.refresh}" }
        activity.window.attributes = activity.window.attributes.also { it.preferredDisplayModeId = pick.id }
        displayHz = pick.refresh
    }

    fun diagnosticState(): String = buildString {
        appendLine("uri=${request.uri}")
        appendLine("headers=${request.headers.keys}")
        appendLine("state=${player.playbackState} pos=${player.currentPosition} dur=${player.duration}")
        appendLine("video=${player.videoFormat} decoder=$decoderName")
        appendLine("audio=${player.audioFormat} passthrough=$passthrough")
        appendLine("dsp=$dspParams speed=$speed displayHz=$displayHz bw=$bandwidthBps")
        appendLine("error=$error")
    }

    /** Result for the launching app; call before finish(). */
    fun resultIntent(completed: Boolean): android.content.Intent =
        com.nexus.player.intent.IntentParser.resultIntent(player.currentPosition, durationMs, completed, request.uri)

    fun release() {
        autosave.cancel()
        save()
        player.release()
    }

    companion object {
        const val LONG_JUMP_MS = 60_000L
        const val AUTOSAVE_MS = 10_000L
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun mimeHint(type: String?): String? = when (type?.lowercase()) {
            "application/x-mpegurl", "application/vnd.apple.mpegurl" -> MimeTypes.APPLICATION_M3U8
            "application/dash+xml" -> MimeTypes.APPLICATION_MPD
            else -> null
        }
    }
}
