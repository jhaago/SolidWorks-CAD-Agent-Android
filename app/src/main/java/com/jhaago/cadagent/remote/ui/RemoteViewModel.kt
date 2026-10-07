package com.jhaago.cadagent.remote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.remote.data.AiControlRepository
import com.jhaago.cadagent.remote.data.RemoteSessionRepository
import com.jhaago.cadagent.remote.display.*
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.live.LiveRemoteSessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RemoteUiState(
    val session: RemoteWorkstationStatus = RemoteWorkstationStatus(),
    val frame: RemoteDisplayFrame? = null,
    val instruction: String = "",
    val error: String? = null,
    val inputMessage: String = "Tap or drag the display to use manual input",
    val artifact: AiArtifact? = null,
    val downloadingArtifact: Boolean = false,
    val photo: CadPhoto? = null,
    val photoLoading: Boolean = false,
) {
    val connected: Boolean get() = session.connection == RemoteConnectionState.Connected
    val canRunTask: Boolean get() = connected && (session.controller != RemoteController.User || session.mode == RemoteControlMode.Assist) &&
        !session.controlPending && !session.task.active && !photoLoading && (photo == null || session.supportsJobImages) &&
        instruction.isNotBlank() && instruction.trim().length <= 2000
}

private data class EditorState(
    val instruction: String = "",
    val error: String? = null,
    val inputMessage: String = "Tap or drag the display to use manual input",
    val artifact: AiArtifact? = null,
    val downloadingArtifact: Boolean = false,
    val photo: CadPhoto? = null,
    val photoLoading: Boolean = false,
)

class RemoteViewModel(
    private val session: RemoteSessionRepository,
    private val ai: AiControlRepository,
    display: RemoteDisplaySource,
    private val input: RemoteInputController,
) : ViewModel() {
    private val editor = MutableStateFlow(EditorState())
    val uiState: StateFlow<RemoteUiState> = combine(session.status, display.frame, editor) { state, frame, edit ->
        RemoteUiState(state, frame, edit.instruction, edit.error, edit.inputMessage, edit.artifact, edit.downloadingArtifact, edit.photo, edit.photoLoading)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RemoteUiState())

    fun connect() {
        session.connect()
    }

    fun disconnect() {
        if (session.status.value.task.active) {
            ai.stopTask()
            editor.value = editor.value.copy(error = "Stop the CAD task before disconnecting so it is not left running on the PC.")
            return
        }
        input.releaseAll()
        session.disconnect()
    }

    fun selectMode(mode: RemoteControlMode) {
        if (session.status.value.task.active) return
        if (mode != session.status.value.mode) input.releaseAll()
        session.setMode(mode)
    }

    fun takeControl() {
        if (session.status.value.task.active) {
            ai.stopTask()
            if (session.status.value.task.active) return
        }
        input.releaseAll()
        session.takeControl()
    }

    fun stopTask() = ai.stopTask()
    fun approvePlan() { editor.value = editor.value.copy(error = if (ai.approvePlan()) null else "Wait for a validated current plan before approving.") }
    fun requestChanges(instructions: String) {
        if (!ai.requestChanges(instructions)) editor.value = editor.value.copy(error = "Enter 1–2000 characters against the current revision.")
        else editor.value = editor.value.copy(error = null)
    }
    fun completeTask() { editor.value = editor.value.copy(error = if (ai.completeTask()) null else "Wait for the job to be ready for review.") }
    fun downloadArtifact() {
        if (editor.value.downloadingArtifact) return
        editor.value = editor.value.copy(downloadingArtifact = true, error = null)
        viewModelScope.launch {
            try {
                val artifact = ai.downloadArtifact()
                editor.value = editor.value.copy(artifact = artifact, error = if (artifact == null) "No native file is available for this job." else null)
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { editor.value = editor.value.copy(error = "The native file could not be downloaded. Refresh the job and try again.") }
            finally { editor.value = editor.value.copy(downloadingArtifact = false) }
        }
    }
    fun artifactSaved(message: String?) { editor.value = editor.value.copy(artifact = null, error = message) }

    fun setForeground(value: Boolean) {
        if (session is LiveRemoteSessionRepository) session.setForeground(value)
        else if (!value) input.releaseAll()
    }

    fun cancelInput() = input.releaseAll()

    fun changeInstruction(value: String) {
        editor.value = editor.value.copy(instruction = value.take(2001), error = null)
    }

    fun attachPhoto(bytes: ByteArray) {
        editor.value = if (bytes.isEmpty() || bytes.size > 4 * 1024 * 1024)
            editor.value.copy(error = "The picture must be at most 4 MiB.")
        else editor.value.copy(photo = CadPhoto(bytes), error = null)
    }

    fun setPhotoLoading(value: Boolean) { editor.value = editor.value.copy(photoLoading = value) }

    fun removePhoto() { editor.value = editor.value.copy(photo = null, error = null) }

    fun photoError(message: String) { editor.value = editor.value.copy(error = message) }

    fun runTask() {
        if (session.status.value.task.active) return
        if (editor.value.photoLoading) {
            editor.value = editor.value.copy(error = "Wait for the picture to finish attaching.")
            return
        }
        if (editor.value.photo != null && !session.status.value.supportsJobImages) {
            editor.value = editor.value.copy(error = "Update the Windows CAD Agent before sending a picture.")
            return
        }
        if ((session.status.value.controller == RemoteController.User && session.status.value.mode != RemoteControlMode.Assist) ||
            session.status.value.controlPending) {
            editor.value = editor.value.copy(error = "Release remote desktop control before starting a CAD task.")
            return
        }
        input.releaseAll()
        if (session.status.value.mode == RemoteControlMode.Manual) session.setMode(RemoteControlMode.Agent)
        val submitted = editor.value.photo?.let { ai.submitTaskWithImage(editor.value.instruction, it) }
            ?: if (editor.value.photo == null) ai.submitTask(editor.value.instruction) else null
        if (submitted == null) {
            editor.value = editor.value.copy(error = "Connect to Windows and enter a CAD instruction of 1–2000 characters.")
        } else {
            editor.value = editor.value.copy(error = null, photo = null)
        }
    }

    fun approve(id: String) { ai.approveProtectedAction(id) }
    fun reject(id: String) { ai.rejectProtectedAction(id) }

    fun pointer(event: RemotePointerEvent): Boolean {
        if (!event.valid) return false
        val ready = interruptForManualInput()
        val accepted = ready && input.sendPointer(event)
        editor.value = editor.value.copy(inputMessage = if (accepted) "Manual input sent" else "Input unavailable: connect, stop any AI task, and resume control")
        return accepted
    }

    fun keyboard(event: RemoteKeyboardEvent): Boolean {
        if (!event.valid) return false
        val ready = interruptForManualInput()
        val accepted = ready && input.sendKeyboard(event)
        editor.value = editor.value.copy(inputMessage = if (accepted) "Manual key sent" else "Input unavailable: connect, stop any AI task, and resume control")
        return accepted
    }

    private fun interruptForManualInput(): Boolean {
        if (session.status.value.task.active) {
            ai.stopTask()
            if (session.status.value.task.active) return false
        }
        if (session.status.value.mode == RemoteControlMode.Agent || session.status.value.controller == RemoteController.Ai) {
            takeControl()
        }
        return !session.status.value.task.active && session.status.value.controller == RemoteController.User
    }

    override fun onCleared() {
        input.releaseAll()
        // The workstation owns durable CAD jobs. Closing this screen must only
        // release the phone's session; Stop AI remains an explicit user action.
        session.disconnect()
    }
}
