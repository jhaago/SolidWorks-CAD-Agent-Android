package com.jhaago.cadagent.remote.data

import com.jhaago.cadagent.remote.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory demo only. All session/task changes publish one immutable authority snapshot. */
class FakeRemoteSessionRepository : RemoteSessionRepository {
    private val mutableStatus = MutableStateFlow(RemoteWorkstationStatus())
    override val status = mutableStatus.asStateFlow()
    private var connectionAttempt = 0L
    private var taskSequence = 0L
    internal var authorityEpoch = 0L
        private set

    @Synchronized override fun connect(): Long? {
        if (status.value.connection != RemoteConnectionState.Disconnected) return null
        connectionAttempt++
        publish(status.value.copy(connection = RemoteConnectionState.Connecting))
        return connectionAttempt
    }

    /** Explicit completion lets tests deliver late/out-of-order fake callbacks deterministically. */
    @Synchronized fun finishConnecting(attempt: Long): Boolean {
        if (attempt != connectionAttempt || status.value.connection != RemoteConnectionState.Connecting) return false
        publish(status.value.copy(connection = RemoteConnectionState.Connected, mode = RemoteControlMode.Manual, controller = RemoteController.User))
        return true
    }

    @Synchronized override fun disconnect() {
        if (status.value.connection == RemoteConnectionState.Disconnected) return
        connectionAttempt++
        publish(stopped(status.value, "Stopped: remote session disconnected").copy(
            connection = RemoteConnectionState.Disconnected, mode = RemoteControlMode.Manual, controller = RemoteController.None,
        ))
    }

    @Synchronized override fun setMode(mode: RemoteControlMode) {
        val current = status.value
        if (current.connection != RemoteConnectionState.Connected || current.mode == mode) return
        publish(stopped(current, "Stopped: control mode changed").copy(mode = mode, controller = idleController(mode)))
    }

    @Synchronized override fun takeControl() {
        if (status.value.connection != RemoteConnectionState.Connected) return
        publish(stopped(status.value, "Stopped: you took control").copy(mode = RemoteControlMode.Manual, controller = RemoteController.User))
    }

    @Synchronized internal fun submitTask(instruction: String): String? {
        val current = status.value
        val text = instruction.trim()
        if (current.connection != RemoteConnectionState.Connected || current.mode == RemoteControlMode.Manual ||
            current.task.active || text.isEmpty() || text.length > 2000) return null
        val id = "demo-task-${++taskSequence}"
        publish(current.copy(
            controller = if (current.mode == RemoteControlMode.Agent) RemoteController.Ai else RemoteController.User,
            task = AiTaskState(id, text, AiTaskPhase.Running, message = "Demo: inspecting the workstation"),
            protectedAction = null,
        ))
        return id
    }

    @Synchronized internal fun advanceTask(id: String): Boolean {
        val current = status.value
        if (current.connection != RemoteConnectionState.Connected || current.task.id != id || current.task.phase != AiTaskPhase.Running) return false
        when {
            current.mode == RemoteControlMode.Assist -> publish(current.copy(task = current.task.copy(
                phase = AiTaskPhase.Completed, step = 1, message = "Demo suggestion: inspect the model and sliced preview before printing.",
            )))
            current.mode != RemoteControlMode.Agent -> return false
            current.task.step == 0 -> publish(current.copy(task = current.task.copy(step = 1, message = "Demo: model prepared; inspecting sliced preview")))
            else -> publish(current.copy(
                controller = RemoteController.None,
                task = current.task.copy(phase = AiTaskPhase.AwaitingProtectedAction, step = 2, message = "Paused: starting a physical print requires your approval"),
                protectedAction = ProtectedActionRequest("$id-print", id, ProtectedActionKind.Start3dPrint),
            ))
        }
        return true
    }

    @Synchronized internal fun decideProtectedAction(id: String, approved: Boolean): Boolean {
        val current = status.value
        val request = current.protectedAction ?: return false
        if (current.connection != RemoteConnectionState.Connected || current.mode != RemoteControlMode.Agent ||
            current.task.phase != AiTaskPhase.AwaitingProtectedAction || request.disposition != ProtectedActionDisposition.Pending ||
            request.id != id || request.taskId != current.task.id) return false
        publish(current.copy(
            controller = RemoteController.None,
            task = current.task.copy(
                phase = if (approved) AiTaskPhase.Completed else AiTaskPhase.Stopped,
                message = if (approved) "Demo approval recorded. No printer command was sent." else "Stopped: print approval rejected",
            ),
            protectedAction = request.copy(disposition = if (approved) ProtectedActionDisposition.Approved else ProtectedActionDisposition.Rejected),
        ))
        return true
    }

    @Synchronized internal fun stopTask() {
        publish(stopped(status.value, "Stopped by you").let {
            it.copy(controller = if (it.connection == RemoteConnectionState.Connected) idleController(it.mode) else RemoteController.None)
        })
    }

    private fun stopped(current: RemoteWorkstationStatus, message: String): RemoteWorkstationStatus = current.copy(
        task = if (current.task.active) current.task.copy(phase = AiTaskPhase.Stopped, message = message) else current.task,
        protectedAction = current.protectedAction?.let {
            if (it.disposition == ProtectedActionDisposition.Pending) it.copy(disposition = ProtectedActionDisposition.Rejected) else it
        },
    )

    private fun idleController(mode: RemoteControlMode) = if (mode == RemoteControlMode.Agent) RemoteController.None else RemoteController.User

    private fun publish(next: RemoteWorkstationStatus) {
        if (status.value.controller == RemoteController.User && next.controller != RemoteController.User) authorityEpoch++
        mutableStatus.value = next
    }
}
