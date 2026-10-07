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
        Text("Remote desktop", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp).testTag("remote-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${state.session.workstationName} · ${state.session.connection}", Modifier.testTag("connection-status"), style = MaterialTheme.typography.bodyMedium)
                    if (!state.session.isLive) {
                        Text("Pair a workstation in Settings before connecting.", Modifier.testTag("unpaired-workstation"))
                    } else if (state.session.connection == RemoteConnectionState.Disconnected) {
                        Button(onClick = actions::connect, modifier = Modifier.testTag("connect-remote")) { Text("Connect") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = actions::disconnect, enabled = !state.session.task.active,
                                modifier = Modifier.testTag("disconnect-remote")) { Text("Disconnect") }
                            if (state.connected && state.frame != null && !state.session.task.active)
                                Button(onClick = actions::takeControl, enabled = !state.session.controlPending && state.session.controller != RemoteController.User,
                                    modifier = Modifier.testTag("take-control")) { Text("Take control") }
                            if (state.connected && state.session.controller == RemoteController.User)
                                OutlinedButton(onClick = actions::cancelInput, modifier = Modifier.testTag("release-control")) { Text("View only") }
                            if (state.session.task.active)
                                OutlinedButton(onClick = actions::stopTask, enabled = state.session.task.phase != AiTaskPhase.Stopping,
                                    modifier = Modifier.testTag("stop-ai")) { Text("Stop CAD task") }
                        }
                        if (state.connected) {
                            Text(when {
                                state.session.task.active -> "CAD task running · view only"
                                state.session.controller == RemoteController.User -> "You control the desktop"
                                else -> "Viewing only"
                            }, Modifier.testTag("controller"), style = MaterialTheme.typography.bodySmall)
                            Text(if (state.session.solidWorksAttached) "SOLIDWORKS attached" else "SOLIDWORKS unavailable",
                                Modifier.testTag("solidworks-status"), style = MaterialTheme.typography.bodySmall)
                        }
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
