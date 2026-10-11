package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.RemoteUiState
import com.jhaago.cadagent.ui.theme.*
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
fun AiTaskPanel(
    state: RemoteUiState, onInstruction: (String) -> Unit, onRun: () -> Unit,
    onApprove: () -> Unit = {}, onRequestChanges: (String) -> Unit = {}, onComplete: () -> Unit = {},
    onDownload: () -> Unit = {}, onStop: () -> Unit = {},
) {
    val task = state.session.task
    var savedDraft by rememberSaveable { mutableStateOf(listOf(task.id.orEmpty(), task.revisionId.orEmpty(), "")) }
    val changes = restoredRevisionDraft(savedDraft, task.id, task.revisionId)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WorkspaceCard {
            SectionHeading("Design request", "Describe the part, dimensions and feature intent.")
            OutlinedTextField(
                value = state.instruction,
                onValueChange = onInstruction,
                label = { Text("CAD instruction") },
                enabled = !state.session.task.active,
                minLines = 3, maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("ai-instruction"),
                isError = state.error != null,
            )
            state.error?.let { Notice(it, error = true) }
            Button(onClick = onRun, enabled = state.canRunTask, modifier = Modifier.fillMaxWidth().testTag("run-ai-task")) {
                Text("Prepare CAD plan")
            }
            Text("Review the proposed plan before the workstation changes a model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WorkspaceCard {
            SectionHeading("Build & review")
            StatusPill(when (task.phase) {
                AiTaskPhase.Idle -> "Ready for a design"
                AiTaskPhase.Running -> "Work in progress"
                AiTaskPhase.Stopping -> "Stopping task"
                AiTaskPhase.AwaitingClarification -> "Clarification needed"
                AiTaskPhase.AwaitingApproval -> "Awaiting your approval"
                AiTaskPhase.ReadyForReview -> "Ready for review"
                AiTaskPhase.AwaitingProtectedAction -> "Protected action paused"
                AiTaskPhase.Completed -> "Completed"
                AiTaskPhase.Stopped -> "Stopped"
            }, color = if (task.phase in setOf(AiTaskPhase.ReadyForReview, AiTaskPhase.Completed)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            Text(state.session.task.message, Modifier.testTag("ai-task-status"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (task.phase in setOf(AiTaskPhase.Running, AiTaskPhase.Stopping)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (task.active) OutlinedButton(onClick = onStop, enabled = task.phase != AiTaskPhase.Stopping,
                modifier = Modifier.testTag("stop-ai")) { Text("Stop CAD task") }
            if (task.id != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectionContainer { Text(task.instruction, style = MaterialTheme.typography.bodyMedium) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    task.revisionNumber?.let { StatusPill("Current revision $it", Modifier.testTag("cad-revision"), MaterialTheme.colorScheme.secondary) }
                    task.planVersion?.let { StatusPill("CAD plan version $it", Modifier.testTag("cad-plan-version"), MaterialTheme.colorScheme.secondary) }
                }
                task.summary?.let { SelectionContainer { Text(it, Modifier.testTag("cad-plan-summary"), style = MaterialTheme.typography.titleMedium) } }
                if (task.assumptions.isNotEmpty()) {
                    SectionHeading("Design assumptions")
                    task.assumptions.forEach { Notice(it) }
                }
                if (task.ambiguities.isNotEmpty()) {
                    SectionHeading("Clarification needed")
                    task.ambiguities.forEach { Notice(it) }
                }
                if (task.proposedCommands.isNotEmpty()) {
                    SectionHeading("Planned operations", "${task.proposedCommands.size} operations · review before approval")
                    task.proposedCommands.forEachIndexed { index, command ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${index + 1}".padStart(2, '0'), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                SelectionContainer(Modifier.weight(1f)) { Text(command, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                if (task.planVersion == 2 && task.planValidated) {
                    Text("To change this new part, start a new CAD task with a new filename.", style = MaterialTheme.typography.bodySmall)
                }
                if (task.canRevise) {
                    OutlinedTextField(changes, { savedDraft = listOf(task.id.orEmpty(), task.revisionId.orEmpty(), it.take(2001)) }, label = { Text("Clarification or requested changes") },
                        modifier = Modifier.fillMaxWidth().testTag("revision-instructions"), maxLines = 4)
                    OutlinedButton(onClick = { onRequestChanges(changes) }, enabled = changes.trim().length in 1..2000 && !task.actionPending,
                        modifier = Modifier.fillMaxWidth().testTag("revise-cad-plan")) { Text("Submit changes for a new plan") }
                }
                if (task.phase == AiTaskPhase.AwaitingApproval) {
                    Button(onClick = onApprove, enabled = task.canApprove, modifier = Modifier.fillMaxWidth().testTag("approve-cad-plan")) {
                        Text("Approve revision ${task.revisionNumber ?: ""}")
                    }
                }
                task.actionError?.let { Notice(it, error = true, modifier = Modifier.testTag("cad-action-error")) }
                if (task.verifications.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SectionHeading("Native verification", "Evidence reported by the Windows CAD Agent")
                    task.verifications.forEach {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${if (it.passed) "Passed" else "Failed"}: ${it.checkName}", color = if (it.passed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                                it.expected?.let { expected -> SelectionContainer { Text("Expected: $expected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                it.actual?.let { actual -> SelectionContainer { Text("Actual: $actual", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                        }
                    }
                }
                task.outputPath?.let { SelectionContainer { Text("Saved native file: $it", Modifier.testTag("cad-saved-path"), style = MaterialTheme.typography.bodySmall) } }
                if (task.phase in setOf(AiTaskPhase.ReadyForReview, AiTaskPhase.Completed) && task.outputPath != null) {
                    OutlinedButton(onClick = onDownload, enabled = !state.downloadingArtifact, modifier = Modifier.fillMaxWidth().testTag("download-cad-file")) {
                        Text(if (state.downloadingArtifact) "Downloading…" else "Download native file")
                    }
                }
                if (task.phase == AiTaskPhase.ReadyForReview) {
                    Text("Review the model in the live desktop preview and the verification evidence above.")
                    Button(onClick = onComplete, enabled = task.canComplete, modifier = Modifier.fillMaxWidth().testTag("complete-cad-job")) { Text("Accept and complete job") }
                }
                if (task.actionPending) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Notice("The Windows CAD Agent runs approved plans. Stopping a task must be confirmed before remote control resumes.")
        }
    }
}
