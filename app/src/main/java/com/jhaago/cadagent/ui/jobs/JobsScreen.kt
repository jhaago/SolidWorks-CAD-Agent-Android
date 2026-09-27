package com.jhaago.cadagent.ui.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.ui.common.displayName

@Composable
fun JobsScreen(
    state: JobsUiState,
    onJobClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        JobsUiState.Loading -> Column(modifier.padding(24.dp)) { CircularProgressIndicator() }
        JobsUiState.Empty -> Column(modifier.padding(24.dp)) {
            Text("All jobs", style = MaterialTheme.typography.headlineLarge)
            Text("No CAD jobs yet.")
        }
        is JobsUiState.Error -> Column(modifier.padding(24.dp)) {
            Text("All jobs", style = MaterialTheme.typography.headlineLarge)
            Text(state.message)
        }
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(job.prompt, style = MaterialTheme.typography.titleMedium)
                        Text(job.state.displayName())
                        if (job.isSimulated) Text("Simulated", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
