package com.jhaago.cadagent.remote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jhaago.cadagent.remote.live.WorkstationSettingsController
import com.jhaago.cadagent.ui.theme.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun WorkstationSettingsScreen(controller: WorkstationSettingsController) {
    val state by controller.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
      ScreenHeader("Workspace settings", "Pair and manage your Windows connection", CadIcons.Settings, Modifier.widthIn(max = 840.dp))
      Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
       WorkspaceCard {
        SectionHeading("Workstation pairing", "Use your private HTTPS address from Tailscale Serve. Tailscale must be connected on Windows and this phone.")
        if (state.paired) StatusPill("Pairing saved", color = MaterialTheme.colorScheme.tertiary)
        OutlinedTextField(state.endpoint, controller::changeEndpoint, label = { Text("HTTPS workstation address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !state.pairing, modifier = Modifier.fillMaxWidth().testTag("workstation-endpoint"))
        OutlinedTextField(state.pairingSecret, controller::changePairingSecret, label = { Text("One-time Windows pairing code") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), enabled = !state.pairing,
            modifier = Modifier.fillMaxWidth().testTag("pairing-secret"))
        if (state.pairing) {
            Notice(state.message.orEmpty())
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = controller::cancelPairing) { Text("Cancel pairing") }
        } else {
            Button(onClick = controller::pair, enabled = state.pairingSecret.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("pair-workstation")) { Text("Pair with Windows") }
            state.message?.let { Notice(it) }
        }
        state.error?.let { Notice(it, modifier = Modifier.testTag("pairing-error"), error = true) }
        Text("Pairing approval happens on Windows. The phone never asks for an OpenAI API key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
       }
       WorkspaceCard {
        SectionHeading("Saved connection", "Reuse the pairing stored securely on this phone.")
        OutlinedButton(onClick = controller::useSaved, enabled = !state.pairing, modifier = Modifier.fillMaxWidth().testTag("use-live")) { Text("Use saved live workstation") }
        TextButton(onClick = controller::forget, enabled = !state.pairing, modifier = Modifier.testTag("forget-workstation"), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Forget saved pairing") }
       }
       WorkspaceCard {
        SectionHeading("Connected workflow")
        Text("CAD Chat prepares plans and lets you clarify, approve, review evidence and download native files.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Remote shows the live Windows desktop. Take control explicitly to send mouse or keyboard input.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
       }
      }
    }
}
