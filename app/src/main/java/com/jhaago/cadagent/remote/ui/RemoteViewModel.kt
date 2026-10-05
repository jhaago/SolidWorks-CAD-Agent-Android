package com.jhaago.cadagent.remote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.*
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.live.LiveRemoteSessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RemoteUiState(
    val session: RemoteWorkstationStatus = RemoteWorkstationStatus(),
    val frame: RemoteDisplayFrame? = null,
    val instruction: String = "",
    val error: String? = null,
    val inputMessage: String = "Tap or drag the demo display to try manual input",
) {
    val connected: Boolean get() = session.connection == RemoteConnectionState.Connected
    val canRunTask: Boolean get() = connected && session.mode != RemoteControlMode.Manual && !session.task.active &&
        instruction.isNotBlank() && instruction.trim().length <= 2000
}

private data class EditorState(
    val instruction: String = "",
    val error: String? = null,
    val inputMessage: String = "Tap or drag the demo display to try manual input",
)

class RemoteViewModel(
    private val session: RemoteSessionRepository,
    private val ai: AiControlRepository,
    display: RemoteDisplaySource,
    private val input: RemoteInputController,
    private val demo: RemoteDemoDriver? = null,
) : ViewModel() {
    private val editor = MutableStateFlow(EditorState())
    private var connectionJob: Job? = null
    val uiState: StateFlow<RemoteUiState> = combine(session.status, display.frame, editor) { state, frame, edit ->
        RemoteUiState(state, frame, edit.instruction, edit.error, edit.inputMessage)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RemoteUiState())

    fun connect() {
        val attempt = session.connect() ?: return
        connectionJob?.cancel()
        if (demo != null) connectionJob = viewModelScope.launch {
            delay(400)
            demo.finishConnecting(attempt)
        }
    }

    fun disconnect() {
        if (session.status.value.task.active) {
            ai.stopTask()
            editor.value = editor.value.copy(error = "Stop the CAD task before disconnecting so it is not left running on the PC.")
            return
        }
        connectionJob?.cancel()
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
            return
        }
        input.releaseAll()
        session.takeControl()
    }

    fun stopTask() = ai.stopTask()

    fun setForeground(value: Boolean) {
        if (session is LiveRemoteSessionRepository) session.setForeground(value)
        else if (!value) input.releaseAll()
    }

    fun cancelInput() = input.releaseAll()

    fun changeInstruction(value: String) {
        editor.value = editor.value.copy(instruction = value.take(2001), error = null)
    }

    fun runTask() {
        if (session.status.value.task.active) return
        input.releaseAll()
        if (ai.submitTask(editor.value.instruction) == null) {
            editor.value = editor.value.copy(error = "Connect, choose Assist or Agent, and enter an instruction of 1–2000 characters.")
        } else {
            editor.value = editor.value.copy(error = null)
        }
    }

    fun advanceDemoTask() { session.status.value.task.id?.let { demo?.advanceTask(it) } }
    fun approve(id: String) { ai.approveProtectedAction(id) }
    fun reject(id: String) { ai.rejectProtectedAction(id) }

    fun pointer(event: RemotePointerEvent): Boolean {
        if (!event.valid) return false
        interruptForManualInput()
        val accepted = input.sendPointer(event)
        editor.value = editor.value.copy(inputMessage = if (accepted) {
            if (session.status.value.isLive) "Manual input sent" else "Demo input: ${event.action} at ${"%.2f".format(event.x)}, ${"%.2f".format(event.y)}"
        } else "Input unavailable: connect, stop any AI task, and resume control")
        return accepted
    }

    fun keyboard(event: RemoteKeyboardEvent): Boolean {
        if (!event.valid) return false
        interruptForManualInput()
        val accepted = input.sendKeyboard(event)
        editor.value = editor.value.copy(inputMessage = if (accepted) {
            if (session.status.value.isLive) "Manual key sent" else "Demo keyboard input recorded"
        } else "Input unavailable: connect, stop any AI task, and resume control")
        return accepted
    }

    private fun interruptForManualInput() {
        if (session.status.value.task.active) {
            ai.stopTask()
            return
        }
        if (session.status.value.mode == RemoteControlMode.Agent || session.status.value.controller == RemoteController.Ai) takeControl()
    }

    override fun onCleared() {
        connectionJob?.cancel()
        input.releaseAll()
        ai.stopTask()
        session.disconnect()
    }
}
