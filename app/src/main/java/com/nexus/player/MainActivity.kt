package com.nexus.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.nexus.player.diag.DiagLog
import com.nexus.player.intent.IntentParser
import com.nexus.player.intent.StreamRequest
import com.nexus.player.player.PlaybackController
import com.nexus.player.player.Prefs
import com.nexus.player.ui.Hud
import com.nexus.player.ui.HudButton
import com.nexus.player.ui.PlayerScreen
import com.nexus.player.ui.glass

@UnstableApi
class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<PlaybackController?>(null)
    private var completed = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) play(StreamRequest(uri.toString(), null, null, emptyMap()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagLog.enabled = Prefs(this).diagnostics
        IntentParser.parse(intent)?.let(::play)
        setContent {
            val c = controller
            if (c != null) PlayerScreen(c, onExportDiagnostics = { exportDiagnostics(c) })
            else Home { openDocument.launch(arrayOf("video/*", "audio/*")) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IntentParser.parse(intent)?.let(::play)
    }

    private fun play(req: StreamRequest) {
        controller?.release()
        completed = false
        controller = PlaybackController(this, req, onCompleted = {
            // Episode finished: hand control (and the "watched" result) back to the launching app.
            completed = true
            if (req.external) finish()
        }).also { it.start() }
    }

    private fun exportDiagnostics(c: PlaybackController) {
        startActivity(Intent.createChooser(DiagLog.export(this, c.diagnosticState()), "Export Diagnostic Package"))
    }

    /** Every exit path (Back, completion) reports progress to the caller if it asked for a result. */
    override fun finish() {
        controller?.takeIf { it.request.external }?.let { setResult(RESULT_OK, it.resultIntent(completed)) }
        super.finish()
    }

    override fun onStop() {
        super.onStop()
        controller?.let { it.player.pause(); it.save() }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun Home(onOpen: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Hud.Obsidian), contentAlignment = Alignment.Center) {
        Column(Modifier.glass().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("NEXUS PLAYER", style = Hud.Label.copy(color = Hud.Cyan))
            Text("Open a stream from Stremio, Nuvio, a browser or a file manager.", style = Hud.Ui.copy(fontSize = 18.sp))
            HudButton(onOpen, Modifier.focusRequester(focus)) { Text("Open local file") }
        }
    }
}
