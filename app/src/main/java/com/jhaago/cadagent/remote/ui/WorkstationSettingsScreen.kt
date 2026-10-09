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

@Composable
fun WorkstationSettingsScreen(controller: WorkstationSettingsController) {
    val state by controller.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Text("Remote workstation", style = MaterialTheme.typography.titleLarge)
        Text("Use your private HTTPS address from Tailscale Serve. Tailscale must be connected on Windows and this phone.")
        OutlinedTextField(state.endpoint, controller::changeEndpoint, label = { Text("HTTPS workstation address") }, singleLine = true,
            enabled = !state.pairing, modifier = Modifier.fillMaxWidth().testTag("workstation-endpoint"))
        OutlinedTextField(state.pairingSecret, controller::changePairingSecret, label = { Text("One-time Windows pairing code") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), enabled = !state.pairing,
            modifier = Modifier.fillMaxWidth().testTag("pairing-secret"))
        if (state.pairing) {
            Text(state.message.orEmpty())
            OutlinedButton(onClick = controller::cancelPairing) { Text("Cancel pairing") }
        } else {
            Button(onClick = controller::pair, enabled = state.pairingSecret.isNotBlank(), modifier = Modifier.testTag("pair-workstation")) { Text("Pair with Windows") }
            OutlinedButton(onClick = controller::useSaved, modifier = Modifier.testTag("use-live")) { Text("Use saved live workstation") }
            OutlinedButton(onClick = controller::forget, modifier = Modifier.testTag("forget-workstation")) { Text("Forget saved pairing") }
            state.message?.let { Text(it) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pairing-error")) }
        Text("Pairing approval happens on Windows. The phone never asks for an OpenAI API key.")
        Text("After pairing, Remote supports viewing, control and CAD jobs: submit instructions, clarify or revise plans, approve, review evidence and download the native file.")
    }
}
