package com.jhaago.cadagent.remote.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jhaago.cadagent.remote.model.AiArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owned by the screen, so scrolling a review card cannot suspend its picker. */
@Composable
fun ArtifactSaveEffect(artifact: AiArtifact?, onSaved: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentArtifact by rememberUpdatedState(artifact)
    val saved by rememberUpdatedState(onSaved)
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = currentArtifact
        if (uri == null || file == null) saved(null)
        else scope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Destination unavailable")
                    output.use { it.write(file.bytes) }
                    null
                } catch (_: Exception) { "Could not save the native file to that destination." }
            }
            saved(error)
        }
    }
    LaunchedEffect(artifact) { artifact?.let { saveFile.launch(it.fileName) } }
}
