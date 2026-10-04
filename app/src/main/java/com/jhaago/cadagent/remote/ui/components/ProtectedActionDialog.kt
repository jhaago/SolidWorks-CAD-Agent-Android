package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jhaago.cadagent.remote.model.ProtectedActionRequest

@Composable
fun ProtectedActionDialog(request: ProtectedActionRequest, onApprove: (String) -> Unit, onReject: (String) -> Unit, onTakeControl: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("protected-action-dialog"),
        onDismissRequest = { onReject(request.id) },
        title = { Text("Approve starting a print?") },
        text = {
            Column {
                Text("The task is paused. Starting a physical 3D print requires your explicit approval.\n\nSimulation only: no printer is connected and no print command will be sent.")
                TextButton(onClick = onTakeControl, modifier = Modifier.testTag("dialog-take-control")) { Text("Take Control") }
            }
        },
        confirmButton = { TextButton(onClick = { onApprove(request.id) }, modifier = Modifier.testTag("approve-protected-action")) { Text("Approve demo") } },
        dismissButton = { TextButton(onClick = { onReject(request.id) }, modifier = Modifier.testTag("reject-protected-action")) { Text("Reject") } },
    )
}
