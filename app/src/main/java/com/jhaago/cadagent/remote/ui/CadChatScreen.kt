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
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun CadChatScreen(state: RemoteUiState, actions: RemoteViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    Column(Modifier.fillMaxSize().testTag("cad-chat-screen")) {
        Text("CAD Chat", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("cad-chat-content"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        when {
                            !state.session.isLive -> "No workstation paired"
                            !state.connected -> "Workstation disconnected"
                            state.session.solidWorksAttached -> "Connected · SOLIDWORKS attached"
                            else -> "Connected · SOLIDWORKS unavailable"
                        },
                        modifier = Modifier.weight(1f).testTag("cad-connection-status"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.session.isLive && state.session.connection == RemoteConnectionState.Disconnected)
                        TextButton(onClick = actions::connect, modifier = Modifier.testTag("cad-connect")) { Text("Connect") }
                }
                if (!state.session.isLive) Text("Pair a workstation in Settings to send CAD requests.")
            }
            if (state.session.isLive) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                AiTaskPanel(state, actions::changeInstruction, actions::runTask,
                    onApprove = actions::approvePlan, onRequestChanges = actions::requestChanges,
                    onComplete = actions::completeTask, onDownload = actions::downloadArtifact,
                    onArtifactSaved = actions::artifactSaved, onStop = actions::stopTask)
            }
        }
    }
}
