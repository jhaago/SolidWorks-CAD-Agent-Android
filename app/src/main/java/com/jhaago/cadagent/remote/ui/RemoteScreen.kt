package com.jhaago.cadagent.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.components.*

@Composable
fun RemoteScreen(state: RemoteUiState, actions: RemoteViewModel) {
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var control by remember { mutableStateOf(false) }
    var viewResetKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.connected) { if (!state.connected) fullScreen = false }
    val available = state.connected && (!state.session.isLive || state.session.controller == RemoteController.User) && !state.session.task.active && !state.session.controlPending
    LaunchedEffect(available) { if (!available) { control = false; actions.cancelInput() } }
    fun changeControl(value: Boolean) { actions.cancelInput(); control = value && available }
    Column(Modifier.fillMaxSize().testTag("remote-screen")) {
        Text("Remote workstation", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
        RemoteControlBar(state.session, actions::takeControl, actions::stopTask, state.frame != null)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).testTag("remote-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.session.isLive) "Live · private HTTPS workstation" else "No live workstation selected", style = MaterialTheme.typography.titleMedium)
                        Text("${state.session.workstationName} · ${state.session.connection}", Modifier.testTag("connection-status"))
                        if (state.session.isLive && state.session.connection == RemoteConnectionState.Disconnected) {
                            Button(onClick = actions::connect, modifier = Modifier.testTag("connect-remote")) {
                                Text("Connect live workstation")
                            }
                        } else {
                            OutlinedButton(
                                onClick = actions::disconnect,
                                enabled = !state.session.task.active,
                                modifier = Modifier.testTag("disconnect-remote"),
                            ) { Text(if (state.connected) "Disconnect" else "Cancel connection") }
                        }
                        if (state.session.isLive && state.connected) {
                            HorizontalDivider()
                            Text("Agent Host: ${when (state.session.agentHostAvailable) { true -> "Available"; false -> "Unavailable"; null -> "Checking…" }}")
                            val execution = state.session.executionMode ?: "Unknown mode"
                            val model = state.session.aiModel ?: "Model not reported"
                            Text("$execution · $model", style = MaterialTheme.typography.bodySmall)
                            Text(
                                when {
                                    state.session.solidWorksAttached -> "SOLIDWORKS attached"
                                    state.session.solidWorksRunning -> "SOLIDWORKS running · not attached"
                                    else -> "SOLIDWORKS not running/attached"
                                },
                                modifier = Modifier.testTag("solidworks-status"),
                            )
                            state.session.solidWorksVersion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text("Document: ${state.session.activeDocument ?: "No active document reported"}", Modifier.testTag("active-document"), style = MaterialTheme.typography.bodySmall)
                        } else if (!state.session.isLive) {
                            Text("Pair a workstation in Settings before connecting.", Modifier.testTag("unpaired-workstation"), style = MaterialTheme.typography.bodySmall)
                        }
                        state.session.message?.let { Text(it) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemoteControlMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.session.mode == mode,
                            onClick = { actions.selectMode(mode) },
                            enabled = state.connected && !state.session.task.active,
                            label = { Text(mode.name) },
                            modifier = Modifier.testTag("mode-${mode.name}"),
                        )
                    }
                }
            }
            if (state.connected && state.frame != null) {
                item {
                    RemoteDisplaySurface(
                        state.frame,
                        actions::pointer,
                        control && available,
                        actions::cancelInput,
                        viewResetKey = viewResetKey,
                    )
                }
                item {
                    OutlinedButton(onClick = { actions.cancelInput(); fullScreen = true }, modifier = Modifier.testTag("enter-full-screen")) { Text("Full screen") }
                    DesktopControls(control, available, ::changeControl, state.frame.cursorX, state.frame.cursorY, actions::pointer, actions::keyboard)
                    TextButton(onClick = { actions.cancelInput(); viewResetKey++ }) { Text("Fit desktop") }
                    Text(state.inputMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.session.isLive) {
                item { AiTaskPanel(state, actions::changeInstruction, actions::runTask, onApprove = actions::approvePlan, onRequestChanges = actions::requestChanges, onComplete = actions::completeTask, onDownload = actions::downloadArtifact, onArtifactSaved = actions::artifactSaved) }
            }
        }
    }
    if (fullScreen && state.connected && state.frame != null) {
        Dialog(onDismissRequest = { actions.cancelInput(); fullScreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize().testTag("full-screen-desktop")) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { actions.cancelInput(); fullScreen = false }, modifier = Modifier.testTag("exit-full-screen")) { Text("Exit") }
                        TextButton(onClick = { actions.cancelInput(); viewResetKey++ }) { Text("Fit") }
                        TextButton(onClick = actions::stopTask, enabled = state.session.task.active, modifier = Modifier.testTag("fullscreen-stop-ai")) { Text("Stop AI") }
                        TextButton(onClick = { actions.cancelInput(); actions.disconnect() }, modifier = Modifier.testTag("fullscreen-stop-remote")) { Text("Stop Remote") }
                    }
                    Text("${state.session.connection} · ${state.session.controller} · ${if (control && available) "Control" else "View"}", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
                    RemoteDisplaySurface(state.frame, actions::pointer, control && available, actions::cancelInput, Modifier.weight(1f).fillMaxWidth(), fullScreen = true, viewResetKey = viewResetKey)
                    DesktopControls(control, available, ::changeControl, state.frame.cursorX, state.frame.cursorY, actions::pointer, actions::keyboard)
                    if (!available && state.connected) TextButton(onClick = actions::takeControl, enabled = !state.session.task.active && !state.session.controlPending) { Text("Resume Control") }
                }
            }
        }
    }
    state.session.protectedAction?.takeIf { it.disposition == ProtectedActionDisposition.Pending }?.let {
        ProtectedActionDialog(it, actions::approve, actions::reject, actions::takeControl)
    }
}
