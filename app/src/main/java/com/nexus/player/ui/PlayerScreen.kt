package com.nexus.player.ui

import android.view.ViewGroup
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.nexus.player.audio.DspParams
import com.nexus.player.audio.Preset
import com.nexus.player.diag.DiagLog
import com.nexus.player.player.PlaybackController
import com.nexus.player.player.PlayerFactory
import com.nexus.player.player.Prefs
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.nexus.player.player.SeekAccumulator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Panel { NONE, SOUND, AUDIO, SUBS, SPEED, SETTINGS }

private const val OVERLAY_TIMEOUT_MS = 5_000L
private const val HUD_TIMEOUT_MS = 1_500L
/** Horizontal swipe across the full screen width seeks this far. */
private const val SWIPE_SEEK_SPAN_MS = 120_000L

@UnstableApi
@Composable
fun PlayerScreen(c: PlaybackController, onExportDiagnostics: () -> Unit) {
    val player = c.player
    var overlay by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var stats by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(0L) }
    var volumeHudAt by remember { mutableLongStateOf(0L) }
    var seekHud by remember { mutableStateOf<String?>(null) }
    var seekHudAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val seeker = remember { SeekAccumulator() }
    var pending by remember { mutableStateOf<Long?>(null) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomHudAt by remember { mutableLongStateOf(0L) }
    var lastEdgeTapAt by remember { mutableLongStateOf(0L) }
    var lastEdgeZone by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var brightnessHudAt by remember { mutableLongStateOf(0L) }
    val window = LocalActivity.current?.window

    // Seeks are coalesced: inputs move a pending target; one real seek fires once input settles.
    fun commitSeek(afterMs: Long) {
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(afterMs)
            seeker.take()?.let(c::seekTo)
            pending = null
        }
    }
    fun showTarget(t: Long, commitAfterMs: Long?) {
        pending = t
        val d = t - player.currentPosition
        seekHud = "${if (d >= 0) "+" else "−"}${kotlin.math.abs(d) / 1000}s  ▸  ${tc(t)}"
        seekHudAt = System.currentTimeMillis()
        lastInput = seekHudAt
        if (commitAfterMs != null) commitSeek(commitAfterMs) else seekJob?.cancel()
    }
    fun queueSeek(deltaMs: Long) =
        showTarget(seeker.nudge(player.currentPosition, c.durationMs, deltaMs), SeekAccumulator.SETTLE_MS)
    fun scrub(t: Long, commit: Boolean) = showTarget(seeker.set(t, c.durationMs), if (commit) 0L else null)

    // 250 ms UI clock; also drives auto-hide.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            position = player.currentPosition
            buffered = player.bufferedPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            playing = player.isPlaying
            if (overlay && panel == Panel.NONE && now - lastInput > OVERLAY_TIMEOUT_MS && player.isPlaying) overlay = false
            delay(250)
        }
    }
    // 0 ms transitions: overlays are plain conditionals, focus moves immediately.
    LaunchedEffect(overlay) {
        if (overlay) runCatching { playFocus.requestFocus() } else { panel = Panel.NONE; runCatching { root.requestFocus() } }
    }

    fun showOverlay() { lastInput = System.currentTimeMillis(); overlay = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .focusRequester(root)
            .onPreviewKeyEvent { e ->
                lastInput = System.currentTimeMillis()
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (c.resumePrompt != null) return@onPreviewKeyEvent false
                if (overlay) {
                    if (e.key == Key.Back) { if (panel != Panel.NONE) panel = Panel.NONE else overlay = false; true } else false
                } else when (e.key) {
                    Key.DirectionUp -> { c.nudgeVolume(0.05f); volumeHudAt = System.currentTimeMillis(); true }
                    Key.DirectionDown -> { c.nudgeVolume(-0.05f); volumeHudAt = System.currentTimeMillis(); true }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val dir = if (e.key == Key.DirectionRight) 1 else -1
                        queueSeek(dir * c.stepMs * SeekAccumulator.multiplier(e.nativeKeyEvent.repeatCount)); true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> { showOverlay(); true }
                    Key.MediaPlayPause, Key.Spacebar -> { if (player.isPlaying) player.pause() else player.play(); true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); true }
                    Key.MediaFastForward -> { queueSeek(c.stepMs * SeekAccumulator.multiplier(e.nativeKeyEvent.repeatCount)); true }
                    Key.MediaRewind -> { queueSeek(-c.stepMs * SeekAccumulator.multiplier(e.nativeKeyEvent.repeatCount)); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            // Zoom = enlarge the video surface around the centre; the root clips the overflow.
            modifier = Modifier.fillMaxSize().layout { m, cons ->
                val w = (cons.maxWidth * zoom).roundToInt()
                val h = (cons.maxHeight * zoom).roundToInt()
                val p = m.measure(Constraints.fixed(w, h))
                layout(cons.maxWidth, cons.maxHeight) { p.place((cons.maxWidth - w) / 2, (cons.maxHeight - h) / 2) }
            },
        )

        // Touch layer above video, below HUD: tap toggles overlay; double-tap outer thirds seeks, centre play/pause.
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { o ->
                        // After an edge double-tap, further single taps on the same edge keep stepping.
                        val zone = doubleTapZone(o.x, size.width.toFloat())
                        val t = System.currentTimeMillis()
                        if (zone != 0 && zone == lastEdgeZone && t - lastEdgeTapAt < EDGE_CHAIN_MS) {
                            lastEdgeTapAt = t; queueSeek(zone * c.stepMs)
                        } else if (overlay) overlay = false else showOverlay()
                    },
                    onDoubleTap = { o ->
                        when (val zone = doubleTapZone(o.x, size.width.toFloat())) {
                            0 -> if (player.isPlaying) player.pause() else player.play()
                            else -> { lastEdgeZone = zone; lastEdgeTapAt = System.currentTimeMillis(); queueSeek(zone * c.stepMs) }
                        }
                    },
                )
            }.pointerInput(Unit) {
                // One-finger drag (MX-style): vertical right half = volume, vertical left half = brightness, horizontal = seek.
                var axis = 0 // 0 undecided, 1 vertical, 2 horizontal
                var right = true
                var total = Offset.Zero
                var startPos = 0L
                var startVol = 0f
                var startBright = 0.5f
                fun end() { if (axis == 2) pending?.let { scrub(it, true) }; axis = 0 }
                detectDragGestures(
                    onDragStart = { o ->
                        axis = 0; total = Offset.Zero; right = o.x >= size.width / 2f
                        startPos = player.currentPosition; startVol = c.dspParams.volume
                        startBright = window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                    },
                    onDragEnd = ::end,
                    onDragCancel = ::end,
                ) { change, d ->
                    total += d
                    if (axis == 0) axis = if (kotlin.math.abs(total.y) > kotlin.math.abs(total.x)) 1 else 2
                    change.consume()
                    val now0 = System.currentTimeMillis()
                    when {
                        axis == 2 && c.durationMs > 0 -> scrub(startPos + (total.x / size.width * SWIPE_SEEK_SPAN_MS).toLong(), false)
                        axis == 1 && right -> {
                            c.updateDsp(c.dspParams.copy(volume = ((startVol - total.y / size.height * 1.5f) * 100).let { Math.round(it) / 100f }.coerceIn(0f, 2f)))
                            volumeHudAt = now0
                        }
                        axis == 1 -> {
                            brightness = (startBright - total.y / size.height * 1.5f).coerceIn(0.01f, 1f)
                            window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = brightness } }
                            brightnessHudAt = now0
                        }
                    }
                }
            }.pointerInput(Unit) {
                // Two-finger pinch: spread to zoom in (100–300 %), pinch to zoom out; snaps to 100 % near fit.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.count { it.pressed } >= 2) {
                            val f = ev.calculateZoom()
                            if (f != 1f) {
                                zoom = nextZoom(zoom, f)
                                zoomHudAt = System.currentTimeMillis()
                                ev.changes.forEach { it.consume() }
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    zoom = snapZoom(zoom)
                }
            },
        )

        if (stats) StatsPanel(c, position, buffered, Modifier.align(Alignment.TopStart).padding(16.dp))
        else if (overlay) Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            formatBadges(c).forEach { (t, color) -> Pill(t, color) }
        }

        if (!(overlay && panel == Panel.SOUND)) Row(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.passthrough) Pill("PASSTHROUGH", Hud.Amber)
            else if (c.dspParams.preset != Preset.FLAT || c.dspParams.voiceBoostDb > 0f) {
                Pill("${c.dspParams.preset.label()} +${fmt1(c.dspParams.voiceBoostDb)} dB", Hud.Mint)
            }
        }

        if (now - volumeHudAt < HUD_TIMEOUT_MS) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(24.dp).height(280.dp).width(56.dp).glass().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val v = c.dspParams.volume
                Text("${Math.round(v * 100)}%", style = Hud.Mono.copy(color = if (v > 1f) Hud.Amber else Hud.Mint))
                VolumeBar(v, Modifier.weight(1f))
                Text(if (c.passthrough) "MAX100" else "VOL", style = Hud.Label)
            }
        }

        if (now - brightnessHudAt < HUD_TIMEOUT_MS) {
            Text("BRIGHTNESS ${Math.round(brightness * 100)}%", style = Hud.Mono.copy(color = Hud.Amber),
                modifier = Modifier.align(Alignment.CenterStart).padding(24.dp).glass().padding(16.dp))
        }

        if (now - zoomHudAt < HUD_TIMEOUT_MS) {
            Text("ZOOM ${Math.round(zoom * 100)}%", style = Hud.Mono, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).glass().padding(16.dp))
        }

        if (seekHud != null && (pending != null || now - seekHudAt < HUD_TIMEOUT_MS)) {
            // Thumbnail preview needs source trickplay; degrade to timecode.
            Text(
                seekHud!!, style = Hud.Mono.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.Center).glass().padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        c.error?.let { err ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLAYBACK ERROR", style = Hud.Label.copy(color = Hud.Amber))
                Text(err, style = Hud.Mono.copy(color = Hud.TextC))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudButton(c::retry) { Text("Retry") }
                    HudButton(onExportDiagnostics) { Text("Export diagnostics") }
                }
            }
        }

        c.resumePrompt?.let { s ->
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            Column(Modifier.align(Alignment.Center).glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Resume from ${tc(s.positionMs)}?", style = Hud.Ui)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudButton({ c.answerResume(true) }, Modifier.focusRequester(first)) { Text("Resume") }
                    HudButton({ c.answerResume(false) }) { Text("Start over") }
                }
            }
        }

        if (overlay && panel == Panel.SOUND) {
            // Right-edge Sound drawer; bottom padding clears the transport bar.
            Column(
                Modifier.align(Alignment.TopEnd).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 176.dp)
                    .widthIn(max = 400.dp).fillMaxHeight().glass().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound", style = Hud.Title)
                    Spacer(Modifier.weight(1f))
                    Text(c.dspParams.preset.label().uppercase(), style = Hud.Mono.copy(color = if (c.passthrough) Hud.Muted else Hud.Mint))
                }
                SoundPanel(c, Modifier.weight(1f))
            }
        }

        if (overlay) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (panel != Panel.NONE && panel != Panel.SOUND) {
                    Box(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                        when (panel) {
                            Panel.SOUND -> Unit
                            Panel.AUDIO -> TrackPanel(c, C.TRACK_TYPE_AUDIO)
                            Panel.SUBS -> TrackPanel(c, C.TRACK_TYPE_TEXT)
                            Panel.SPEED -> SpeedPanel(c)
                            Panel.SETTINGS -> SettingsPanel(c, onExportDiagnostics)
                            Panel.NONE -> Unit
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeekBar(
                        pos = pending ?: position, buf = buffered, dur = duration,
                        onNudge = { dir, repeat -> queueSeek(dir * c.stepMs * SeekAccumulator.multiplier(repeat)) },
                        onScrub = ::scrub,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tc(pending ?: position), style = Hud.Mono.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (pending != null) Hud.Amber else Hud.TextC))
                        Text(" / ${tc(duration)}", style = Hud.Mono.copy(fontSize = 15.sp, color = Hud.Muted))
                        Spacer(Modifier.weight(1f))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { HudButton({ queueSeek(-c.stepMs) }, icon = HudIcons.Back, label = "${c.prefs.seekStepSec}s") }
                            item {
                                HudButton(
                                    { if (player.isPlaying) player.pause() else player.play() }, Modifier.focusRequester(playFocus),
                                    icon = if (playing) HudIcons.Pause else HudIcons.Play,
                                )
                            }
                            item { HudButton({ queueSeek(c.stepMs) }, icon = HudIcons.Forward, label = "${c.prefs.seekStepSec}s") }
                            fun toggle(p: Panel) { panel = if (panel == p) Panel.NONE else p }
                            item { HudButton({ toggle(Panel.SOUND) }, selected = panel == Panel.SOUND, icon = HudIcons.Sound, label = "Sound") }
                            item { HudButton({ toggle(Panel.AUDIO) }, selected = panel == Panel.AUDIO, icon = HudIcons.Audio, label = "Audio") }
                            item { HudButton({ toggle(Panel.SUBS) }, selected = panel == Panel.SUBS, icon = HudIcons.Subs, label = "Subs") }
                            item { HudButton({ toggle(Panel.SPEED) }, selected = panel == Panel.SPEED, icon = HudIcons.Speed, label = "${fmt2(c.speed)}×") }
                            if (zoom != 1f) item { HudButton({ zoom = 1f; zoomHudAt = System.currentTimeMillis() }) { Text("Zoom ${Math.round(zoom * 100)}% · Reset") } }
                            item { HudButton({ stats = !stats }, selected = stats, icon = HudIcons.Stats, label = "Stats") }
                            item { HudButton({ toggle(Panel.SETTINGS) }, selected = panel == Panel.SETTINGS, icon = HudIcons.Settings, label = "Settings") }
                        }
                    }
                }
            }
        }
    }
}

/** Focusable + touch seek bar: D-pad Left/Right steps (hold accelerates); tap jumps; drag scrubs, commits on release. */
@Composable
private fun SeekBar(pos: Long, buf: Long, dur: Long, onNudge: (Int, Int) -> Unit, onScrub: (Long, Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val d = dur.coerceAtLeast(1).toFloat()
    fun at(x: Float, w: Int) = (x / w.coerceAtLeast(1)).coerceIn(0f, 1f).let { (it * d).toLong() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(if (focused) 3.dp else 0.dp, if (focused) Hud.Cyan else Color.Transparent, Hud.Shape)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent {
                if (it.type != KeyEventType.KeyDown || dur <= 0) return@onKeyEvent false
                when (it.key) {
                    Key.DirectionLeft -> { onNudge(-1, it.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight -> { onNudge(1, it.nativeKeyEvent.repeatCount); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(dur) { detectTapGestures { if (dur > 0) onScrub(at(it.x, size.width), true) } }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = it.x },
                    onDragEnd = { if (dur > 0) onScrub(at(dragX, size.width), true) },
                ) { change, _ -> dragX = change.position.x; if (dur > 0) onScrub(at(dragX, size.width), false) }
            }
            .padding(horizontal = 8.dp),
    ) {
        val y = size.height / 2
        val h = 6.dp.toPx()
        fun bar(frac: Float, color: Color) = drawRoundRect(
            color, Offset(0f, y - h / 2), Size(size.width * frac.coerceIn(0f, 1f), h), CornerRadius(h / 2),
        )
        bar(1f, Color(0x14FFFFFF))
        bar(buf / d, Color(0x4000F2FE))
        bar(pos / d, Hud.Cyan)
        drawCircle(if (focused) Hud.Cyan else Hud.TextC, radius = if (focused) 8.dp.toPx() else 6.dp.toPx(),
            center = Offset(size.width * (pos / d).coerceIn(0f, 1f), y))
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(text, style = Hud.Mono.copy(color = color), modifier = Modifier.glass().padding(horizontal = 8.dp, vertical = 4.dp))
}

@UnstableApi
@Composable
private fun StatsPanel(c: PlaybackController, pos: Long, buf: Long, modifier: Modifier) {
    val v = c.player.videoFormat
    val a = c.player.audioFormat
    val rows = listOf(
        "ENGINE" to "Media3 · ${c.decoderName}",
        "VIDEO" to (v?.let { "${it.width}×${it.height} · ${it.sampleMimeType?.substringAfter('/')} · ${fmt3(it.frameRate)}" } ?: "—"),
        "BITRATE" to (v?.bitrate?.takeIf { it > 0 }?.let { "${fmt1(it / 1e6f)} Mbps" } ?: "—"),
        "NETWORK" to (if (c.bandwidthBps > 0) "${fmt1(c.bandwidthBps / 1e6f)} Mbps" else "—"),
        "BUFFER" to "${fmt1((buf - pos).coerceAtLeast(0) / 1000f)} s / ${PlayerFactory.FORWARD_BUFFER_MS / 1000} s · back ${PlayerFactory.BACK_BUFFER_MS / 1000} s",
        "AUDIO" to (a?.let { "${it.sampleMimeType?.substringAfter('/')} · ${it.channelCount} ch" + if (c.passthrough) " · passthrough" else " · PCM" } ?: "—"),
        "DISPLAY" to "${fmt3(c.displayHz)} Hz" + if (c.prefs.afr) " · AFR" else "",
    )
    Column(modifier.glass().padding(horizontal = 16.dp, vertical = 8.dp)) {
        rows.forEach { (k, value) ->
            Row { Text(k, style = Hud.Mono.copy(color = Hud.Muted), modifier = Modifier.width(88.dp)); Text(value, style = Hud.Mono) }
        }
    }
}

@UnstableApi
@Composable
private fun SoundPanel(c: PlaybackController, modifier: Modifier = Modifier) {
    val p = c.dspParams
    val locked = c.passthrough
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (locked) item { Text("Bitstream passthrough active — DSP bypassed by the audio sink.", style = Hud.Ui.copy(color = Hud.Amber)) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Preset.entries) { pr ->
                    HudButton({ c.updateDsp(p.copy(preset = pr)) }, selected = p.preset == pr, enabled = !locked) { Text(pr.label()) }
                }
            }
        }
        item {
            StepSlider("Voice Boost", p.voiceBoostDb, 0f, 12f, 0.5f, "${fmt1(p.voiceBoostDb)} dB",
                { c.updateDsp(c.dspParams.copy(voiceBoostDb = it)) }, enabled = !locked)
        }
        item {
            val night = p.preset == Preset.NIGHT
            HudButton({ c.updateDsp(c.dspParams.copy(preset = if (night) Preset.FLAT else Preset.NIGHT)) },
                selected = night, enabled = !locked, label = if (night) "Night mode (DRC) · on" else "Night mode (DRC) · off")
        }
        item { EqBars(p.eqDb, active = p.preset == Preset.CUSTOM && !locked) }
        item {
            StepSlider("Volume", p.volume, 0f, 2f, 0.05f, "${Math.round(p.volume * 100)}%",
                { c.updateDsp(c.dspParams.copy(volume = it)) }, accent = if (p.volume > 1f) Hud.Amber else Hud.Mint)
        }
        if (p.preset == Preset.CUSTOM) {
            items(5) { i ->
                val f = DspParams.EQ_FREQS[i]
                StepSlider(if (f >= 1000) "${(f / 1000).toInt()} kHz" else "${f.toInt()} Hz", p.eqDb[i], -12f, 12f, 1f,
                    "${if (p.eqDb[i] > 0) "+" else ""}${p.eqDb[i].toInt()} dB",
                    { v -> c.updateDsp(c.dspParams.copy(eqDb = c.dspParams.eqDb.toMutableList().also { it[i] = v })) },
                    accent = Hud.Cyan, enabled = !locked)
            }
        }
    }
}

@UnstableApi
@Composable
private fun TrackPanel(c: PlaybackController, type: Int) {
    val groups = c.tracks.groups.filter { it.type == type && it.isSupported }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (type == C.TRACK_TYPE_TEXT) {
            item {
                val off = c.trackParams.disabledTrackTypes.contains(type)
                HudButton({ c.selectTrack(type, null) }, selected = off) { Text("Off") }
            }
        }
        items(groups) { g ->
            val f = g.getTrackFormat(0)
            val label = listOfNotNull(f.label, f.language, f.sampleMimeType?.substringAfter('/'),
                f.channelCount.takeIf { it > 0 }?.let { "${it}ch" }).joinToString(" · ").ifEmpty { "Track" }
            HudButton({ c.selectTrack(type, g) }, selected = g.isSelected) { Text(label) }
        }
        if (groups.isEmpty()) item { Text("No tracks", style = Hud.Ui.copy(color = Hud.Muted)) }
    }
}

@UnstableApi
@Composable
private fun SpeedPanel(c: PlaybackController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Prefs.SPEEDS) { s -> HudButton({ c.setSpeed(s, c.perItemSpeed) }, selected = c.speed == s) { Text("${fmt2(s)}×") } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton({ c.setSpeed(c.speed, false) }, selected = !c.perItemSpeed) { Text("Global") }
            HudButton({ c.setSpeed(c.speed, true) }, selected = c.perItemSpeed) { Text("This video only") }
        }
    }
}

@UnstableApi
@Composable
private fun SettingsPanel(c: PlaybackController, onExport: () -> Unit) {
    var step by remember { mutableIntStateOf(c.prefs.seekStepSec) }
    var auto by remember { mutableStateOf(c.prefs.autoResume) }
    var afr by remember { mutableStateOf(c.prefs.afr) }
    var diag by remember { mutableStateOf(DiagLog.enabled) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Seek step", style = Hud.Ui, modifier = Modifier.width(112.dp))
            Prefs.SEEK_STEPS.forEach { s -> HudButton({ step = s; c.prefs.seekStepSec = s }, selected = step == s) { Text("${s}s") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resume", style = Hud.Ui, modifier = Modifier.width(112.dp))
            HudButton({ auto = true; c.prefs.autoResume = true }, selected = auto) { Text("Auto") }
            HudButton({ auto = false; c.prefs.autoResume = false }, selected = !auto) { Text("Ask") }
            Spacer(Modifier.width(16.dp))
            HudButton({ afr = !afr; c.prefs.afr = afr }, selected = afr) { Text(if (afr) "AFR on" else "AFR off") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnostics", style = Hud.Ui, modifier = Modifier.width(112.dp))
            HudButton({ diag = !diag; DiagLog.enabled = diag; c.prefs.diagnostics = diag }, selected = diag) {
                Text(if (diag) "Logging on" else "Logging off")
            }
            HudButton(onExport) { Text("Export Diagnostic Package") }
        }
        UpdateRow()
    }
}

/** 5-band EQ gains, −12…+12 dB around a centre line. Dimmed unless Custom 5-Band is active. */
@Composable
private fun EqBars(db: List<Float>, active: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (active) "5-BAND EQ" else "5-BAND EQ · CUSTOM ONLY", style = Hud.Label)
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val slot = size.width / 5f
            val bw = slot * 0.6f
            val mid = size.height / 2
            drawLine(Hud.GlassLine, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
            db.forEachIndexed { i, g ->
                val h = (g.coerceIn(-12f, 12f) / 12f) * mid
                val top = if (h >= 0) mid - h else mid
                drawRoundRect(
                    (if (g >= 0) Hud.Cyan else Hud.Amber).copy(alpha = if (active) 1f else 0.35f),
                    Offset(i * slot + (slot - bw) / 2, top), Size(bw, kotlin.math.abs(h).coerceAtLeast(2.dp.toPx())),
                    CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row {
            DspParams.EQ_FREQS.forEach { f ->
                Text(if (f >= 1000) "${(f / 1000).toInt()}k" else "${f.toInt()}", style = Hud.Mono.copy(fontSize = 11.sp, color = Hud.Muted),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Top-left format badges: resolution (+HDR), frame rate (mint), bitrate (amber). */
@UnstableApi
private fun formatBadges(c: PlaybackController): List<Pair<String, Color>> {
    val v = c.player.videoFormat ?: return emptyList()
    return listOfNotNull(
        resolutionLabel(v.width, v.height, v.colorInfo?.colorTransfer, v.sampleMimeType)?.let { it to Hud.TextC },
        v.frameRate.takeIf { it > 0f }?.let { "${fmt3(it)} fps" to Hud.Mint },
        v.bitrate.takeIf { it > 0 }?.let { "${fmt1(it / 1e6f)} Mbps" to Hud.Amber },
    )
}

/** Width-aware so scope films (3840×1600, 1920×800) get their class label, not their letterboxed height. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun resolutionLabel(width: Int, height: Int, transfer: Int?, mime: String?): String? {
    val res = when {
        width >= 3200 || height >= 2000 -> "4K"
        width >= 2400 || height >= 1400 -> "1440p"
        width >= 1800 || height >= 1000 -> "1080p"
        width >= 1200 || height >= 700 -> "720p"
        height > 0 -> "${height}p"
        else -> return null
    }
    val dyn = when {
        mime == "video/dolby-vision" -> " Dolby Vision"
        transfer == C.COLOR_TRANSFER_ST2084 -> " HDR10"
        transfer == C.COLOR_TRANSFER_HLG -> " HLG"
        else -> ""
    }
    return res + dyn
}

private fun Preset.label() = when (this) {
    Preset.FLAT -> "Off (Flat)"
    Preset.DIALOGUE -> "Dialogue Boost"
    Preset.VOCAL_ISOLATION -> "Vocal Isolation"
    Preset.NIGHT -> "Night Mode"
    Preset.CUSTOM -> "Custom 5-Band"
}

private const val EDGE_CHAIN_MS = 1_000L

internal fun nextZoom(current: Float, factor: Float): Float = (current * factor).coerceIn(1f, 3f)

internal fun snapZoom(z: Float): Float = if (z < 1.04f) 1f else z

/** -1 = left third (back), 1 = right third (forward), 0 = centre. */
internal fun doubleTapZone(x: Float, width: Float): Int = when {
    x < width / 3f -> -1
    x > width * 2f / 3f -> 1
    else -> 0
}

internal fun tc(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

private fun fmt1(v: Float) = "%.1f".format(v)
private fun fmt2(v: Float) = "%.2f".format(v)
private fun fmt3(v: Float) = if (v <= 0f) "—" else "%.3f".format(v)
