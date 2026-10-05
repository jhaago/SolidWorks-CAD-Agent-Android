package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.RemoteUiState

@Composable
fun AiTaskPanel(state: RemoteUiState, onInstruction: (String) -> Unit, onRun: () -> Unit, onAdvance: () -> Unit) {
    val live = state.session.isLive
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (live) "AI control · Live workstation" else "AI control · Simulation", style = MaterialTheme.typography.titleMedium)
            Text(when (state.session.mode) {
                RemoteControlMode.Manual -> "Choose Assist or Agent before sending a CAD instruction."
                RemoteControlMode.Assist -> if (live) "Send a bounded CAD task while keeping the remote view available." else "You keep control. The demo AI offers a suggestion."
                RemoteControlMode.Agent -> if (live) "The PC agent executes the CAD task. Stop AI before taking manual control." else "Try a bounded task, then take control whenever you need to."
            })
            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstruction,
                label = { Text("Workstation instruction") },
                enabled = !state.session.task.active,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("ai-instruction"),
                isError = state.error != null,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onRun, enabled = state.canRunTask, modifier = Modifier.testTag("run-ai-task")) {
                Text(if (live) "Run CAD task" else "Run demo task")
            }
            Text(state.session.task.message, Modifier.testTag("ai-task-status"))
            if (!live && state.session.task.phase == AiTaskPhase.Running) {
                OutlinedButton(onClick = onAdvance, modifier = Modifier.testTag("advance-demo-task")) { Text("Next demo step") }
            }
            Text(
                if (live) "Live tasks run through the paired Windows CAD Agent. Cancellation must be confirmed before manual input is re-enabled."
                else "This demonstration uses fixed steps. It does not interpret your instruction or control a real workstation.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
