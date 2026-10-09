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
                Text("Workstation pairing", style = MaterialTheme.typography.titleMedium)
                Text("Pair a Windows workstation to enable live remote access.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Remote connection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Configure the HTTPS workstation address and one-time pairing code in the Remote workstation settings.",
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
