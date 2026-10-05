package com.jhaago.cadagent.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.components.*

@Composable
fun RemoteScreen(state: RemoteUiState, actions: RemoteViewModel) {
    var key by remember { mutableStateOf("Enter") }
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
                        Text(if (state.session.isLive) "Live · private HTTPS workstation" else "Simulation · no live PC connection", style = MaterialTheme.typography.titleMedium)
                        Text("${state.session.workstationName} · ${state.session.connection}", Modifier.testTag("connection-status"))
                        if (state.session.connection == RemoteConnectionState.Disconnected) {
                            Button(onClick = actions::connect, modifier = Modifier.testTag("connect-remote")) {
                                Text(if (state.session.isLive) "Connect live workstation" else "Connect demo")
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
                            Text("CAD jobs remain available in Home and Jobs.", style = MaterialTheme.typography.bodySmall)
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
                            enabled = state.connected && !state.session.task.active && state.session.controller != RemoteController.User,
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
                        (!state.session.isLive || state.session.controller == RemoteController.User) && !state.session.task.active,
                        actions::cancelInput,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.inputMessage, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { actions.pointer(RemotePointerEvent(PointerAction.Click, .5f, .5f, PointerButton.Secondary)) }) { Text("Right click") }
                            OutlinedButton(onClick = { actions.pointer(RemotePointerEvent(PointerAction.Scroll, .5f, .5f, scrollY = -1f)) }) { Text("Scroll ↑") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { key = it.take(64) },
                                label = { Text("Key, e.g. Enter") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    actions.keyboard(RemoteKeyboardEvent(key, KeyAction.Down))
                                    actions.keyboard(RemoteKeyboardEvent(key, KeyAction.Up))
                                },
                                enabled = key.isNotBlank() && !state.session.task.active,
                            ) { Text("Send key") }
                        }
                    }
                }
            }
            item { AiTaskPanel(state, actions::changeInstruction, actions::runTask, actions::advanceDemoTask) }
        }
    }
    state.session.protectedAction?.takeIf { it.disposition == ProtectedActionDisposition.Pending }?.let {
        ProtectedActionDialog(it, actions::approve, actions::reject, actions::takeControl)
    }
}
