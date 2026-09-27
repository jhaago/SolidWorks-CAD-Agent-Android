package com.jhaago.cadagent.ui.jobdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.ui.common.displayName

@Composable
fun JobDetailScreen(
    state: JobDetailUiState,
    onApprove: () -> Unit,
    onRequestChanges: (String) -> Unit,
    onCancel: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        JobDetailUiState.Loading -> Column(modifier.padding(24.dp)) {
            CircularProgressIndicator()
        }
        is JobDetailUiState.Error -> Column(
            modifier = modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Job unavailable", style = MaterialTheme.typography.headlineLarge)
            Text(state.message)
            if (state.recoverable) Button(onClick = onReload) { Text("Reload") }
            TextButton(onClick = onBack) { Text("Back to jobs") }
        }
        is JobDetailUiState.Content -> JobDetailContent(
            state = state,
            onApprove = onApprove,
            onRequestChanges = onRequestChanges,
            onCancel = onCancel,
            onReload = onReload,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun JobDetailContent(
    state: JobDetailUiState.Content,
    onApprove: () -> Unit,
    onRequestChanges: (String) -> Unit,
    onCancel: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val job = state.job
    val revision = job.currentRevision
    var changeInstructions by rememberSaveable(revision?.id) { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("CAD Job", style = MaterialTheme.typography.headlineLarge)
            Text(job.state.displayName(), style = MaterialTheme.typography.titleMedium)
            if (job.isSimulated) Text("Simulated workflow", style = MaterialTheme.typography.labelMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Request", style = MaterialTheme.typography.titleMedium)
                    Text(job.prompt)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current plan", style = MaterialTheme.typography.titleMedium)
                    if (revision == null) {
                        Text("No plan revision is available.")
                    } else {
                        Text("Revision ${revision.sequence}")
                        Text(revision.id, style = MaterialTheme.typography.labelSmall)
                        revision.actions.forEachIndexed { index, action ->
                            Text("${index + 1}. $action")
                        }
                        revision.changeInstructions?.let { Text("Requested change: $it") }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Plan checks", style = MaterialTheme.typography.titleMedium)
                    Text(if (job.planValidated) "Plan validated" else "Plan not yet validated")
                    if (job.hasUnresolvedAmbiguity) {
                        Text(job.ambiguityMessage ?: "Clarification is required.")
                    } else {
                        Text("No unresolved ambiguity")
                    }
                    job.outputPath?.let { Text("Result: $it") }
                }
            }
        }
        state.actionError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onReload) { Text("Reload current revision") }
            }
        }
        if (state.canRequestChanges) {
            item {
                OutlinedTextField(
                    value = changeInstructions,
                    onValueChange = { changeInstructions = it },
                    label = { Text("Requested changes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedButton(
                    onClick = { onRequestChanges(changeInstructions) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Request Changes")
                }
            }
        }
        if (state.canApprove) {
            item {
                Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                    Text("Approve Build")
                }
            }
        }
        if (state.canCancel) {
            item {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel Job")
                }
            }
        }
        if (state.isActionInProgress) {
            item { Text("Updating job…") }
        }
        item {
            TextButton(onClick = onBack, enabled = !state.isActionInProgress) {
                Text("Back to jobs")
            }
        }
    }
}
