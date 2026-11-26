package com.nexus.player.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
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

private enum class Panel { NONE, SOUND, AUDIO, SUBS, SPEED, SETTINGS }

private const val OVERLAY_TIMEOUT_MS = 5_000L
private const val HUD_TIMEOUT_MS = 1_500L

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
                        c.seekBy(dir)
                        seekHud = "${if (dir > 0) "+" else "−"}${c.prefs.seekStepSec} s · ${tc(player.currentPosition)}"
                        seekHudAt = System.currentTimeMillis(); true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> { showOverlay(); true }
                    Key.MediaPlayPause, Key.Spacebar -> { if (player.isPlaying) player.pause() else player.play(); true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); true }
                    Key.MediaFastForward -> { c.seekBy(1); true }
                    Key.MediaRewind -> { c.seekBy(-1); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { if (overlay) overlay = false else showOverlay() } },
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
            modifier = Modifier.fillMaxSize(),
        )

        if (stats) StatsPanel(c, position, buffered, Modifier.align(Alignment.TopStart).padding(16.dp))

        Row(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        if (!overlay && seekHud != null && now - seekHudAt < HUD_TIMEOUT_MS) {
            // Thumbnail preview needs source trickplay; degrade to timecode.
            Text(seekHud!!, style = Hud.Mono, modifier = Modifier.align(Alignment.Center).glass().padding(16.dp))
        }

        c.error?.let { err ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).glass().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PLAYBACK ERROR", style = Hud.Label.copy(color = Hud.Amber))
                Text(err, style = Hud.Mono.copy(color = Hud.TextC))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudButton({ player.prepare(); player.play() }) { Text("Retry") }
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

        if (overlay) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (panel != Panel.NONE) {
                    Box(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                        when (panel) {
                            Panel.SOUND -> SoundPanel(c)
                            Panel.AUDIO -> TrackPanel(c, C.TRACK_TYPE_AUDIO)
                            Panel.SUBS -> TrackPanel(c, C.TRACK_TYPE_TEXT)
                            Panel.SPEED -> SpeedPanel(c)
                            Panel.SETTINGS -> SettingsPanel(c, onExportDiagnostics)
                            Panel.NONE -> Unit
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Timeline(position, buffered, duration)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tc(position), style = Hud.Mono.copy(color = Hud.TextC))
                        Text(" / ${tc(duration)}", style = Hud.Mono.copy(color = Hud.Muted))
                        Spacer(Modifier.weight(1f))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { HudButton({ c.seekBy(-1) }) { Text("−${c.prefs.seekStepSec}s") } }
                            item {
                                HudButton({ if (player.isPlaying) player.pause() else player.play() }, Modifier.focusRequester(playFocus)) {
                                    Text(if (playing) "Pause" else "Play")
                                }
                            }
                            item { HudButton({ c.seekBy(1) }) { Text("+${c.prefs.seekStepSec}s") } }
                            fun toggle(p: Panel) { panel = if (panel == p) Panel.NONE else p }
                            item { HudButton({ toggle(Panel.SOUND) }, selected = panel == Panel.SOUND) { Text("Sound") } }
                            item { HudButton({ toggle(Panel.AUDIO) }, selected = panel == Panel.AUDIO) { Text("Audio") } }
                            item { HudButton({ toggle(Panel.SUBS) }, selected = panel == Panel.SUBS) { Text("Subs") } }
                            item { HudButton({ toggle(Panel.SPEED) }, selected = panel == Panel.SPEED) { Text("${fmt2(c.speed)}×") } }
                            item { HudButton({ stats = !stats }, selected = stats) { Text("Stats") } }
                            item { HudButton({ toggle(Panel.SETTINGS) }, selected = panel == Panel.SETTINGS) { Text("Settings") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(pos: Long, buf: Long, dur: Long) {
    val d = dur.coerceAtLeast(1).toFloat()
    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x14FFFFFF), Hud.Shape)) {
        Box(Modifier.fillMaxWidth((buf / d).coerceIn(0f, 1f)).height(6.dp).background(Color(0x4000F2FE), Hud.Shape))
        Box(Modifier.fillMaxWidth((pos / d).coerceIn(0f, 1f)).height(6.dp).background(Hud.Cyan, Hud.Shape))
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
private fun SoundPanel(c: PlaybackController) {
    val p = c.dspParams
    val locked = c.passthrough
    LazyColumn(Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val groups = c.player.currentTracks.groups.filter { it.type == type && it.isSupported }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (type == C.TRACK_TYPE_TEXT) {
            item {
                val off = c.player.trackSelectionParameters.disabledTrackTypes.contains(type)
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
    }
}

private fun Preset.label() = when (this) {
    Preset.FLAT -> "Off (Flat)"
    Preset.DIALOGUE -> "Dialogue Boost"
    Preset.VOCAL_ISOLATION -> "Vocal Isolation"
    Preset.NIGHT -> "Night Mode"
    Preset.CUSTOM -> "Custom 5-Band"
}

internal fun tc(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

private fun fmt1(v: Float) = "%.1f".format(v)
private fun fmt2(v: Float) = "%.2f".format(v)
private fun fmt3(v: Float) = if (v <= 0f) "—" else "%.3f".format(v)
