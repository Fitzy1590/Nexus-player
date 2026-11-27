package com.nexus.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.nexus.player.update.UpdateState
import com.nexus.player.update.Updater
import kotlinx.coroutines.launch

/** "Updates" row: installed version, status, and the one action that fits the current state. */
@Composable
fun UpdateRow() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Updater.state
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Updates", style = Hud.Ui, modifier = Modifier.width(112.dp))
        val (msg, color) = when (s) {
            UpdateState.Idle -> "v${Updater.installedVersion(ctx)}" to Hud.Muted
            UpdateState.Checking -> "Checking…" to Hud.Muted
            UpdateState.UpToDate -> "v${Updater.installedVersion(ctx)} · up to date" to Hud.Mint
            is UpdateState.Available -> "v${s.release.version} available" to Hud.Cyan
            is UpdateState.Downloading -> "Downloading ${s.percent}%" to Hud.Amber
            UpdateState.Installing -> "Installing…" to Hud.Amber
            is UpdateState.Failed -> s.message to Hud.Amber
        }
        when (s) {
            is UpdateState.Available -> HudButton({ scope.launch { Updater.install(ctx, s.release) } }, label = "Install v${s.release.version}")
            is UpdateState.Downloading, UpdateState.Installing, UpdateState.Checking -> Unit
            else -> HudButton({ scope.launch { Updater.check(ctx, force = true) } }, label = "Check for updates")
        }
        Text(msg, style = Hud.Mono.copy(color = color))
    }
}
