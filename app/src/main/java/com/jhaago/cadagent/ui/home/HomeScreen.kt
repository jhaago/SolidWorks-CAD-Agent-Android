package com.jhaago.cadagent.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.model.AgentAvailability
import com.jhaago.cadagent.model.CadJob
import com.jhaago.cadagent.ui.common.displayName

@Composable
fun HomeScreen(
    state: HomeUiState,
    onNewJob: () -> Unit,
    onJobClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> Column(modifier.padding(24.dp)) {
            CircularProgressIndicator()
        }
        is HomeUiState.Error -> Column(modifier.padding(24.dp)) {
            Text("Unable to load CAD Agent", style = MaterialTheme.typography.headlineSmall)
            Text(state.message)
        }
        is HomeUiState.Content -> LazyColumn(
            modifier = modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("CAD Agent", style = MaterialTheme.typography.headlineLarge)
                Text("SOLIDWORKS companion", style = MaterialTheme.typography.bodyMedium)
            }
            item { AgentStatusCard(state) }
            item {
                Button(onClick = onNewJob, modifier = Modifier.fillMaxWidth()) {
                    Text("New CAD Job")
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Recent jobs", style = MaterialTheme.typography.titleLarge)
            }
            items(state.recentJobs.size) { index ->
                JobCard(state.recentJobs[index], onJobClick)
            }
        }
    }
}

@Composable
private fun AgentStatusCard(state: HomeUiState.Content) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("System status", style = MaterialTheme.typography.titleMedium)
            val availability = when (state.agentStatus.availability) {
                AgentAvailability.Available -> "Available"
                AgentAvailability.Unavailable -> "Unavailable"
                AgentAvailability.NotConfigured -> "Not configured"
                AgentAvailability.Unknown -> "Unknown"
            }
            Text("Agent: $availability")
            val solidWorks = when (state.agentStatus.solidWorksConnected) {
                true -> "Connected"
                false -> "Disconnected"
                null -> "Unknown"
            }
            Text("SOLIDWORKS: $solidWorks")
            state.agentStatus.solidWorksVersion?.let { Text(it) }
        }
    }
}

@Composable
private fun JobCard(job: CadJob, onJobClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onJobClick(job.id) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(job.prompt, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(job.state.displayName(), style = MaterialTheme.typography.bodyMedium)
                if (job.isSimulated) Text("Simulated", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
