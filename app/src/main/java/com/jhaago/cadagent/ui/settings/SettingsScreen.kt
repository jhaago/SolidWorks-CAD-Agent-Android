package com.jhaago.cadagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current mode", style = MaterialTheme.typography.titleMedium)
                Text("Simulated CAD Agent")
                Text(
                    "This first Android milestone uses local in-memory job data. It does not connect to your Windows PC or SOLIDWORKS yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Future remote connection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Remote pairing is intentionally not configured in this build. A separate security milestone will add authenticated HTTPS pairing to a trusted Windows-side gateway before live remote control is enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI credentials", style = MaterialTheme.typography.titleMedium)
                Text(
                    "OpenAI API credentials stay on the trusted Agent Host. This Android app does not request or store an OpenAI API key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
