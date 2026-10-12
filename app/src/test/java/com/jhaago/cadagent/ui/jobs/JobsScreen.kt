package com.jhaago.cadagent.ui.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.ui.components.JobStateBadge
import com.jhaago.cadagent.ui.components.ScreenLoading
import com.jhaago.cadagent.ui.components.ScreenMessage

@Composable
fun JobsScreen(
    state: JobsUiState,
    onJobClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        JobsUiState.Loading -> ScreenLoading(label = "Loading jobs…", modifier = modifier)
        JobsUiState.Empty -> ScreenMessage(
            title = "All jobs",
            message = "No CAD jobs yet. Create a new job from Home.",
            modifier = modifier,
        )
        is JobsUiState.Error -> ScreenMessage(
            title = "Unable to load jobs",
            message = state.message,
            modifier = modifier,
        )
        is JobsUiState.Content -> LazyColumn(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("All jobs", style = MaterialTheme.typography.headlineLarge) }
            items(state.jobs.size) { index ->
                val job = state.jobs[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJobClick(job.id) },
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(job.prompt, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JobStateBadge(job.state)
                            if (job.isSimulated) Text("Simulated", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
