package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.RemoteUiState

@Composable
fun AiTaskPanel(
    state: RemoteUiState, onInstruction: (String) -> Unit, onRun: () -> Unit,
    onApprove: () -> Unit = {}, onRequestChanges: (String) -> Unit = {}, onComplete: () -> Unit = {},
    onDownload: () -> Unit = {}, onArtifactSaved: (String?) -> Unit = {}, onStop: () -> Unit = {},
) {
    val task = state.session.task
    var changes by remember(task.id, task.revisionId) { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artifact by rememberUpdatedState(state.artifact)
    val saved by rememberUpdatedState(onArtifactSaved)
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = artifact
        if (uri == null || file == null) saved(null)
        else scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Destination unavailable")
                    output.use { it.write(file.bytes) }
                    null
                } catch (_: Exception) { "Could not save the native file to that destination." }
            }
            saved(error)
        }
    }
    LaunchedEffect(state.artifact) { state.artifact?.let { saveFile.launch(it.fileName) } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Describe the CAD work", style = MaterialTheme.typography.titleMedium)
            Text("Review the proposed plan before the workstation changes a model.")
            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstruction,
                label = { Text("CAD instruction") },
                enabled = !state.session.task.active,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("ai-instruction"),
                isError = state.error != null,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onRun, enabled = state.canRunTask, modifier = Modifier.testTag("run-ai-task")) {
                Text("Prepare CAD plan")
            }
            Text(state.session.task.message, Modifier.testTag("ai-task-status"))
            if (task.active) OutlinedButton(onClick = onStop, enabled = task.phase != AiTaskPhase.Stopping,
                modifier = Modifier.testTag("stop-ai")) { Text("Stop CAD task") }
            if (task.id != null) {
                Text(task.instruction, style = MaterialTheme.typography.bodyMedium)
                task.revisionNumber?.let { Text("Current revision $it", Modifier.testTag("cad-revision")) }
                task.planVersion?.let { Text("CAD plan version $it", Modifier.testTag("cad-plan-version")) }
                task.summary?.let { Text(it, Modifier.testTag("cad-plan-summary"), style = MaterialTheme.typography.titleSmall) }
                if (task.assumptions.isNotEmpty()) {
                    Text("Assumptions", style = MaterialTheme.typography.titleSmall)
                    task.assumptions.forEach { Text("• $it") }
                }
                if (task.ambiguities.isNotEmpty()) {
                    Text("Clarification needed", style = MaterialTheme.typography.titleSmall)
                    task.ambiguities.forEach { Text("• $it") }
                }
                if (task.proposedCommands.isNotEmpty()) {
                    Text("Command plan", style = MaterialTheme.typography.titleSmall)
                    task.proposedCommands.forEachIndexed { index, command -> Text("${index + 1}. $command") }
                }
                if (task.planVersion == 2 && task.planValidated) {
                    Text("To change this new part, start a new CAD task with a new filename.", style = MaterialTheme.typography.bodySmall)
                }
                if (task.canRevise) {
                    OutlinedTextField(changes, { changes = it.take(2001) }, label = { Text("Clarification or requested changes") },
                        modifier = Modifier.fillMaxWidth().testTag("revision-instructions"), maxLines = 4)
                    OutlinedButton(onClick = { onRequestChanges(changes) }, enabled = changes.trim().length in 1..2000 && !task.actionPending,
                        modifier = Modifier.testTag("revise-cad-plan")) { Text("Submit changes for a new plan") }
                }
                if (task.phase == AiTaskPhase.AwaitingApproval) {
                    Button(onClick = onApprove, enabled = task.canApprove, modifier = Modifier.testTag("approve-cad-plan")) {
                        Text("Approve revision ${task.revisionNumber ?: ""}")
                    }
                }
                task.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("cad-action-error")) }
                if (task.verifications.isNotEmpty()) {
                    Text("Native verification", style = MaterialTheme.typography.titleSmall)
                    task.verifications.forEach {
                        Text("${if (it.passed) "Passed" else "Failed"}: ${it.checkName}")
                        it.expected?.let { expected -> Text("Expected: $expected", style = MaterialTheme.typography.bodySmall) }
                        it.actual?.let { actual -> Text("Actual: $actual", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                task.outputPath?.let { Text("Saved native file: $it", Modifier.testTag("cad-saved-path")) }
                if (task.phase in setOf(AiTaskPhase.ReadyForReview, AiTaskPhase.Completed) && task.outputPath != null) {
                    OutlinedButton(onClick = onDownload, enabled = !state.downloadingArtifact, modifier = Modifier.testTag("download-cad-file")) {
                        Text(if (state.downloadingArtifact) "Downloading…" else "Download native file")
                    }
                }
                if (task.phase == AiTaskPhase.ReadyForReview) {
                    Text("Review the model in the live desktop preview and the verification evidence above.")
                    Button(onClick = onComplete, enabled = task.canComplete, modifier = Modifier.testTag("complete-cad-job")) { Text("Accept and complete job") }
                }
                if (task.actionPending) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text("The Windows CAD Agent runs approved plans. Stopping a task must be confirmed before remote control resumes.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
