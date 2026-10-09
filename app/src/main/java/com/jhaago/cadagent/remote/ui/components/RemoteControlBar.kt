package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.model.*

@Composable
fun RemoteControlBar(state: RemoteWorkstationStatus, onTakeControl: () -> Unit, onStop: () -> Unit, controlAvailable: Boolean = true) {
    val connected = state.connection == RemoteConnectionState.Connected
    val controller = when (state.controller) {
        RemoteController.User -> "You"
        RemoteController.Ai -> "AI"
        RemoteController.None -> "No controller"
    }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Control: $controller", Modifier.testTag("controller"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTakeControl,
                    enabled = connected && controlAvailable && !state.controlPending && (!state.task.active || !state.isLive) && state.controller != RemoteController.User,
                    modifier = Modifier.testTag("take-control"),
                ) { Text(if (state.isLive) "Resume Control" else "Take Control") }
                OutlinedButton(
                    onClick = onStop,
                    enabled = connected && state.task.active && state.task.phase != AiTaskPhase.Stopping,
                    modifier = Modifier.testTag("stop-ai"),
                ) { Text(if (state.task.phase == AiTaskPhase.Stopping) "Stopping…" else "Stop AI") }
            }
        }
    }
}
