package com.jhaago.cadagent.ui.newjob

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NewJobScreen(
    state: NewJobUiState,
    onPromptChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("New CAD Job", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Describe the part or CAD change you want. The Agent will turn this into a reviewable plan before any build is approved.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            enabled = !state.isSubmitting,
            label = { Text("CAD request") },
            minLines = 5,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSubmitting) "Submitting…" else "Submit CAD Job")
        }
        TextButton(onClick = onBack, enabled = !state.isSubmitting) {
            Text("Back")
        }
    }
}
