package com.jhaago.cadagent.remote.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jhaago.cadagent.remote.model.RemoteConnectionState
import com.jhaago.cadagent.remote.ui.components.AiTaskPanel
import com.jhaago.cadagent.remote.ui.components.ArtifactSaveEffect
import com.jhaago.cadagent.ui.theme.*
import androidx.compose.ui.Alignment
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun CadChatScreen(state: RemoteUiState, actions: RemoteViewModel, onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ArtifactSaveEffect(state.artifact, actions::artifactSaved)
    var showPhotoChoice by remember { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun attach(uri: Uri, deleteAfter: File? = null) {
        actions.setPhotoLoading(true)
        scope.launch {
            try { actions.attachPhoto(loadCadPhoto(context, uri)) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { actions.photoError("Could not attach that picture. Choose a readable photo and try again.") }
            finally { actions.setPhotoLoading(false); deleteAfter?.delete() }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attach(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraPath?.let(::File)
        cameraPath = null
        if (saved && file != null) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.cadphotos", file)
            attach(uri, file)
        } else file?.delete()
    }
    if (showPhotoChoice) AlertDialog(
        onDismissRequest = { showPhotoChoice = false },
        title = { Text("Add a reference picture") },
        text = { Text("Choose a photo from your gallery or take a new one. The CAD plan will include this picture.") },
        confirmButton = {
            TextButton(onClick = { showPhotoChoice = false; gallery.launch("image/*") }, modifier = Modifier.testTag("choose-gallery")) { Text("Gallery") }
        },
        dismissButton = {
            TextButton(onClick = {
                showPhotoChoice = false
                try {
                    val folder = File(context.cacheDir, "cad-photos").apply { mkdirs() }
                    val file = File.createTempFile("cad-", ".jpg", folder)
                    cameraPath = file.absolutePath
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.cadphotos", file))
                } catch (_: Exception) {
                    cameraPath?.let { File(it).delete() }
                    cameraPath = null
                    actions.photoError("The camera could not be opened.")
                }
            }, modifier = Modifier.testTag("take-photo")) { Text("Camera") }
        },
    )
    Column(Modifier.fillMaxSize().imePadding().testTag("cad-chat-screen"), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(
            Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 20.dp).testTag("cad-chat-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item(key = "header") { ScreenHeader("CAD Chat", "Describe. Review. Build.", CadIcons.Model, horizontalPadding = 0.dp) }
            item(key = "connection") {
                WorkspaceCard {
                    SectionHeading("Workstation", if (state.session.isLive) state.session.workstationName else "Connect your engineering workspace")
                    Text(
                        when {
                            !state.session.isLive -> "No workstation paired"
                            state.session.connection == RemoteConnectionState.Connecting -> "Connecting to workstation…"
                            !state.connected -> "Workstation disconnected"
                            state.session.solidWorksAttached -> "Connected · SOLIDWORKS attached"
                            else -> "Connected · SOLIDWORKS unavailable"
                        },
                        modifier = Modifier.testTag("cad-connection-status"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.session.isLive && state.session.connection == RemoteConnectionState.Disconnected)
                        TextButton(onClick = actions::connect, modifier = Modifier.testTag("cad-connect")) { Text("Connect") }
                    if (state.session.connection == RemoteConnectionState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!state.session.isLive) {
                        Text("Pair a workstation in Settings to send CAD requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().testTag("open-pairing-settings")) { Text("Set up workstation") }
                    }
                }
            }
            if (!state.session.isLive) item(key = "intro") {
                WorkspaceCard {
                    SectionHeading("From an idea to a CAD model", "Send dimensions or a reference picture to your paired PC.")
                    Text("01  Describe the part and its dimensions", style = MaterialTheme.typography.bodyMedium)
                    Text("02  Clarify requirements and review the plan", style = MaterialTheme.typography.bodyMedium)
                    Text("03  Approve, inspect and download the result", style = MaterialTheme.typography.bodyMedium)
                    Notice("Planning and execution run on Windows. You review and approve the work here.")
                }
            }
            if (state.session.isLive) {
              item(key = "reference") {
                WorkspaceCard {
                  SectionHeading("Reference picture", "Optional · gallery or camera")
                  FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showPhotoChoice = true }, enabled = !state.session.task.active && !state.photoLoading,
                        modifier = Modifier.testTag("add-picture")) { Text("Add picture") }
                    if (state.photo != null) TextButton(onClick = actions::removePhoto,
                        modifier = Modifier.testTag("remove-picture")) { Text("Remove picture") }
                }
                state.photo?.let { photo ->
                    val bitmap = remember(photo) {
                        BitmapFactory.decodeByteArray(photo.jpegBytes, 0, photo.jpegBytes.size,
                            BitmapFactory.Options().apply { inSampleSize = 8 })
                    }
                    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Attached CAD reference picture",
                        modifier = Modifier.size(96.dp).testTag("attached-picture"))
                    Text("Picture attached to the next CAD task", style = MaterialTheme.typography.bodySmall)
                    if (!state.session.supportsJobImages)
                        Text("Update the Windows CAD Agent to send pictures.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.photoLoading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("photo-loading"))
                }
              }
              item(key = "task") {
                AiTaskPanel(state, actions::changeInstruction, actions::runTask,
                    onApprove = actions::approvePlan, onRequestChanges = actions::requestChanges,
                    onComplete = actions::completeTask, onDownload = actions::downloadArtifact,
                    onStop = actions::stopTask)
              }
            }
        }
    }
}
