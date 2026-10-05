package com.jhaago.cadagent.remote.live

import android.graphics.BitmapFactory
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import java.time.OffsetDateTime
import java.util.ArrayDeque
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Owns one paired origin. Input is never replayed; network jobs share an owning coroutine scope. */
class LiveConnectionDriver(
    private val scope: CoroutineScope,
    private val transport: RemoteTransport,
    private val workstation: PairedWorkstation,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val validateImage: (RemoteDisplayFrame) -> Boolean = ::validateJpeg,
) : AutoCloseable {
    private val owned = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + owned)
    private val gate = Any()
    private val mutableStatus = MutableStateFlow(RemoteWorkstationStatus(workstationName = java.net.URI(workstation.endpoint.origin).host, isLive = true))
    private val mutableFrame = MutableStateFlow<RemoteDisplayFrame?>(null)
    val status = mutableStatus.asStateFlow()
    val frame = mutableFrame.asStateFlow()
    private var wanted = false
    private var foreground = false
    private var attempt = 0L
    private var controlVersion = 0L
    private var allowInput = false
    private var sequence = 0L
    private var frameValidUntil = Long.MIN_VALUE
    private var renewAt = Long.MAX_VALUE
    private var grant: Grant? = null
    private var connectionJob: Job? = null
    private var controlJob: Job? = null
    private var inputJob: Job? = null
    private var runtime: CoroutineScope? = null
    private var aiGeneration = 0L
    private var stopRequestedGeneration: Long? = null
    private val queue = ArrayDeque<QueuedInput>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private data class Session(val id: String, val epoch: Long, val controlling: Boolean)
    private class Grant(val token: String, val session: Session) { override fun toString() = "Remote session grant" }
    private data class QueuedInput(val payload: Map<String, Any?>, val version: Long, val generation: Long)
    private data class AgentRuntime(
        val available: Boolean,
        val executionMode: String?,
        val model: String?,
        val solidWorksRunning: Boolean,
        val solidWorksAttached: Boolean,
        val solidWorksVisible: Boolean,
        val solidWorksVersion: String?,
        val activeDocument: String?,
        val activeJob: AiTaskState?,
    )

    fun setForeground(value: Boolean) = synchronized(gate) {
        if (foreground == value) return@synchronized
        foreground = value
        if (!value) stopConnection(keepWanted = true)
        else if (wanted) startConnection()
    }

    fun connect(): Long? = synchronized(gate) {
        if (!foreground || connectionJob?.isActive == true) return@synchronized null
        wanted = true
        startConnection()
        attempt
    }

    fun disconnect() = synchronized(gate) { stopConnection(keepWanted = false) }

    private fun startConnection() {
        val id = ++attempt
        connectionJob = work.launch {
            var retry = 0
            while (current(id)) {
                synchronized(gate) { publish(RemoteConnectionState.Connecting, message = if (retry == 0) "Connecting securely…" else "Reconnecting…") }
                try {
                    val opened = readGrant(transport.call(workstation.endpoint, RemoteOperation("session/create", "POST", "Device", workstation.credential, mapOf("deviceId" to workstation.deviceId))).body)
                    synchronized(gate) {
                        if (!current(id)) return@launch
                        grant = opened
                        renewAt = nowMillis() + 240000
                        allowInput = false
                        publish(RemoteConnectionState.Connected, message = "Viewing only. Resume control or choose an AI mode.")
                    }
                    try { refreshAgentStatus(id, opened) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { markAgentUnavailable(id, opened) }
                    retry = 0
                    coroutineScope {
                        synchronized(gate) { runtime = this }
                        launch { heartbeatLoop(id) }
                        launch { frameLoop(id) }
                        synchronized(gate) { restartInputWorker(id) }
                        awaitCancellation()
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    synchronized(gate) {
                        if (!current(id)) return@launch
                        val old = grant
                        invalidateAuthority()
                        grant = null
                        runtime = null
                        mutableFrame.value = null
                        publish(RemoteConnectionState.Connecting, message = safeMessage(error))
                        old?.let { cleanup(it, "session/close") }
                    }
                    delay(longArrayOf(1000, 2000, 4000, 8000, 15000)[minOf(retry++, 4)])
                }
            }
        }
    }

    private fun current(id: Long) = synchronized(gate) { id == attempt && wanted && foreground }

    private fun stopConnection(keepWanted: Boolean) {
        if (!keepWanted) wanted = false
        attempt++
        connectionJob?.cancel()
        connectionJob = null
        controlJob?.cancel()
        val old = grant
        invalidateAuthority()
        grant = null
        runtime = null
        mutableFrame.value = null
        publish(RemoteConnectionState.Disconnected, message = if (keepWanted && wanted && !foreground) "Paused while Remote is not visible." else null)
        old?.let { cleanup(it, "session/close") }
    }

    fun setMode(mode: RemoteControlMode) = synchronized(gate) {
        val state = mutableStatus.value
        if (state.connection != RemoteConnectionState.Connected) return@synchronized
        if (state.task.active) {
            mutableStatus.value = state.copy(message = "Stop the CAD task before changing control mode.")
            return@synchronized
        }
        if (state.controller == RemoteController.User || state.controlPending) {
            mutableStatus.value = state.copy(message = "Release manual control before changing control mode.")
            return@synchronized
        }
        mutableStatus.value = state.copy(
            mode = mode,
            controller = RemoteController.None,
            message = when (mode) {
                RemoteControlMode.Manual -> "Manual mode. Resume control when ready."
                RemoteControlMode.Assist -> "Assist mode ready for a CAD instruction."
                RemoteControlMode.Agent -> "Agent mode ready. You can take control after stopping the task."
            },
        )
    }

    fun submitAiTask(instruction: String): String? = synchronized(gate) {
        val trimmed = instruction.trim()
        val state = mutableStatus.value
        val captured = grant
        if (trimmed.isBlank() || trimmed.length > 2000 || captured == null || !foreground ||
            state.connection != RemoteConnectionState.Connected || state.mode == RemoteControlMode.Manual || state.task.active) return@synchronized null

        val generation = ++aiGeneration
        stopRequestedGeneration = null
        val requestId = "pending:${UUID.randomUUID()}"
        mutableStatus.value = state.copy(
            controller = if (state.mode == RemoteControlMode.Agent) RemoteController.Ai else RemoteController.None,
            task = AiTaskState(requestId, trimmed, AiTaskPhase.Running, message = "Submitting CAD task…"),
            message = "CAD task submitted to the workstation.",
        )
        val id = attempt
        work.launch {
            try {
                val job = readJob(transport.call(workstation.endpoint, RemoteOperation("agent/jobs", "POST", "Session", captured.token, mapOf("prompt" to trimmed))).body)
                val shouldCancel = synchronized(gate) {
                    if (!current(id) || grant?.token != captured.token || generation != aiGeneration) return@synchronized true
                    if (stopRequestedGeneration == generation) true
                    else {
                        applyJob(job, generation)
                        false
                    }
                }
                if (shouldCancel) cancelCreatedJob(id, captured, generation, job)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                synchronized(gate) {
                    if (generation == aiGeneration) {
                        mutableStatus.value = mutableStatus.value.copy(
                            controller = RemoteController.None,
                            task = mutableStatus.value.task.copy(phase = AiTaskPhase.Stopped, message = safeAgentMessage(error)),
                            message = safeAgentMessage(error),
                        )
                        stopRequestedGeneration = null
                    }
                }
            }
        }
        requestId
    }

    fun stopAiTask() {
        val dispatch = synchronized(gate) {
            val state = mutableStatus.value
            val captured = grant
            if (!state.task.active || captured == null) return
            val generation = aiGeneration
            stopRequestedGeneration = generation
            mutableStatus.value = state.copy(
                controller = RemoteController.None,
                task = state.task.copy(phase = AiTaskPhase.Stopping, message = "Stopping CAD task…"),
                message = "Waiting for the workstation to confirm cancellation…",
            )
            val remoteId = state.task.id?.takeUnless { it.startsWith("pending:") }
            if (remoteId == null) return
            Triple(attempt, captured, Pair(generation, remoteId))
        }
        work.launch { cancelKnownJob(dispatch.first, dispatch.second, dispatch.third.first, dispatch.third.second) }
    }

    private suspend fun cancelCreatedJob(id: Long, captured: Grant, generation: Long, job: AiTaskState) {
        val jobId = job.id ?: return
        cancelKnownJob(id, captured, generation, jobId)
    }

    private suspend fun cancelKnownJob(id: Long, captured: Grant, generation: Long, jobId: String) {
        try {
            val cancelled = readJob(transport.call(workstation.endpoint, RemoteOperation("agent/jobs/$jobId/cancel", "POST", "Session", captured.token)).body)
            synchronized(gate) {
                if (!current(id) || grant?.token != captured.token || generation != aiGeneration) return@synchronized
                mutableStatus.value = mutableStatus.value.copy(
                    controller = RemoteController.None,
                    task = cancelled.copy(phase = AiTaskPhase.Stopped, message = "CAD task cancelled"),
                    message = "CAD task cancellation confirmed.",
                )
                stopRequestedGeneration = null
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            synchronized(gate) {
                if (generation == aiGeneration) {
                    mutableStatus.value = mutableStatus.value.copy(
                        controller = RemoteController.None,
                        task = mutableStatus.value.task.copy(phase = AiTaskPhase.Stopping, message = "Cancellation not yet confirmed"),
                        message = "Cancellation was not confirmed. Retry Stop AI or check the PC before taking manual control.",
                    )
                }
            }
        }
    }

    fun resumeControl() = synchronized(gate) {
        val captured = grant ?: return@synchronized
        if (mutableStatus.value.task.active) {
            mutableStatus.value = mutableStatus.value.copy(message = "Stop the CAD task and wait for cancellation confirmation before taking manual control.")
            return@synchronized
        }
        if (!freshFrame() || !foreground || mutableStatus.value.controlPending) return@synchronized
        invalidateAuthority()
        val version = controlVersion
        val id = attempt
        mutableStatus.value = mutableStatus.value.copy(controlPending = true, mode = RemoteControlMode.Manual, message = "Requesting control…")
        controlJob = work.launch {
            try {
                val session = readSession(JSONObject(transport.call(workstation.endpoint, RemoteOperation("session/resume", "POST", "Session", captured.token)).body))
                synchronized(gate) {
                    if (!current(id) || version != controlVersion || grant?.token != captured.token) return@launch
                    if (!freshFrame() || !session.controlling) { releaseAll(); return@launch }
                    grant = Grant(captured.token, session)
                    sequence = 0
                    allowInput = true
                    mutableStatus.value = mutableStatus.value.copy(controller = RemoteController.User, mode = RemoteControlMode.Manual, controlPending = false, message = "Manual control active")
                    restartInputWorker(id)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                synchronized(gate) {
                    if (version == controlVersion) {
                        releaseAll()
                        mutableStatus.value = mutableStatus.value.copy(controlPending = false, message = safeMessage(error))
                    }
                }
            }
        }
    }

    fun releaseAll() = synchronized(gate) {
        val old = grant
        val shouldRelease = allowInput || mutableStatus.value.controlPending || queue.isNotEmpty()
        invalidateAuthority()
        if (mutableStatus.value.connection == RemoteConnectionState.Connected) {
            mutableStatus.value = mutableStatus.value.copy(controller = RemoteController.None, controlPending = false, message = "Viewing only. Resume control when ready.")
        }
        if (shouldRelease && old != null) {
            // End the entire old lease before attempting network cleanup. A failed
            // release must never be kept alive by otherwise healthy heartbeats.
            cleanup(old, "session/release")
            stopConnection(keepWanted = true)
            if (foreground && wanted) startConnection()
        }
    }

    private fun invalidateAuthority() {
        allowInput = false
        controlVersion++
        sequence = 0
        queue.clear()
        inputJob?.cancel()
        controlJob?.cancel()
    }

    private fun cleanup(old: Grant, route: String) {
        scope.launch {
            try {
                withTimeout(2500) {
                    transport.call(workstation.endpoint, RemoteOperation(route, "POST", "Session", old.token,
                        if (route == "session/release") mapOf("authorityEpoch" to old.session.epoch) else emptyMap()))
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Windows lease expiry is the release fallback when offline. */ }
        }
    }

    private suspend fun heartbeatLoop(id: Long) {
        while (current(id)) {
            delay(1000)
            val old = synchronized(gate) { grant } ?: return
            if (nowMillis() >= renewAt) {
                val renewed = readGrant(transport.call(workstation.endpoint, RemoteOperation("session/renew", "POST", "Session", old.token,
                    mapOf("deviceId" to workstation.deviceId), workstation.credential)).body)
                synchronized(gate) { if (current(id) && grant?.token == old.token) { grant = renewed; renewAt = nowMillis() + 240000 } }
            }
            val captured = synchronized(gate) { grant } ?: return
            val state = readSession(JSONObject(transport.call(workstation.endpoint, RemoteOperation("session/heartbeat", "POST", "Session", captured.token)).body))
            synchronized(gate) {
                if (!current(id) || grant?.token != captured.token) return@synchronized
                val latestEpoch = grant!!.session.epoch
                if (state.epoch >= latestEpoch) {
                    if (allowInput && (state.epoch != latestEpoch || !state.controlling)) {
                        releaseAll()
                        return@synchronized
                    }
                    grant = Grant(captured.token, state)
                }
                if (mutableFrame.value != null && !freshFrame()) {
                    releaseAll()
                    if (!current(id)) return@synchronized
                    mutableFrame.value = null
                    mutableStatus.value = mutableStatus.value.copy(message = "Desktop image is stale. Waiting for a current view.")
                }
            }
            try {
                val latest = synchronized(gate) { grant } ?: return
                refreshAgentStatus(id, latest)
                refreshActiveJob(id, latest)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                val latest = synchronized(gate) { grant } ?: return
                markAgentUnavailable(id, latest)
            }
        }
    }

    private suspend fun refreshAgentStatus(id: Long, captured: Grant) {
        val runtime = readAgentStatus(transport.call(workstation.endpoint, RemoteOperation("agent/status", "GET", "Session", captured.token)).body)
        synchronized(gate) {
            if (!current(id) || grant?.token != captured.token) return@synchronized
            val currentTask = mutableStatus.value.task
            val pendingLocal = currentTask.id?.startsWith("pending:") == true || currentTask.phase == AiTaskPhase.Stopping
            val remoteTask = runtime.activeJob
            val nextMode = if (!pendingLocal && remoteTask?.active == true && mutableStatus.value.mode == RemoteControlMode.Manual) RemoteControlMode.Agent else mutableStatus.value.mode
            val nextTask = if (!pendingLocal && remoteTask != null) remoteTask else currentTask
            mutableStatus.value = mutableStatus.value.copy(
                agentHostAvailable = runtime.available,
                executionMode = runtime.executionMode,
                aiModel = runtime.model,
                solidWorksRunning = runtime.solidWorksRunning,
                solidWorksAttached = runtime.solidWorksAttached,
                solidWorksVisible = runtime.solidWorksVisible,
                solidWorksVersion = runtime.solidWorksVersion,
                activeDocument = runtime.activeDocument,
                mode = nextMode,
                controller = if (nextTask.active && nextMode == RemoteControlMode.Agent) RemoteController.Ai else mutableStatus.value.controller,
                task = nextTask,
            )
        }
    }

    private suspend fun refreshActiveJob(id: Long, captured: Grant) {
        val snapshot = synchronized(gate) { mutableStatus.value.task }
        val jobId = snapshot.id?.takeUnless { it.startsWith("pending:") } ?: return
        if (!snapshot.active) return
        val job = readJob(transport.call(workstation.endpoint, RemoteOperation("agent/jobs/$jobId", "GET", "Session", captured.token)).body)
        synchronized(gate) {
            if (!current(id) || grant?.token != captured.token || snapshot.id != mutableStatus.value.task.id || snapshot.phase == AiTaskPhase.Stopping) return@synchronized
            applyJob(job, aiGeneration)
        }
    }

    private fun markAgentUnavailable(id: Long, captured: Grant) = synchronized(gate) {
        if (!current(id) || grant?.token != captured.token) return@synchronized
        mutableStatus.value = mutableStatus.value.copy(agentHostAvailable = false)
    }

    private fun applyJob(job: AiTaskState, generation: Long) {
        if (generation != aiGeneration) return
        val mode = mutableStatus.value.mode
        mutableStatus.value = mutableStatus.value.copy(
            controller = if (job.active && mode == RemoteControlMode.Agent) RemoteController.Ai else RemoteController.None,
            task = job,
            message = job.message,
        )
        if (!job.active) stopRequestedGeneration = null
    }

    private suspend fun frameLoop(id: Long) {
        while (current(id)) {
            val started = nowMillis()
            val captured = synchronized(gate) { grant } ?: return
            val response = try {
                transport.call(workstation.endpoint, RemoteOperation("display/frame", "GET", "Session", captured.token))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (synchronized(gate) { current(id) && grant?.token != captured.token }) continue
                throw error
            }
            val parsed = readFrame(response.body)
            synchronized(gate) {
                if (!current(id) || grant?.token != captured.token) return@synchronized
                val budget = 3000 - parsed.second
                if (nowMillis() - started >= budget) return@synchronized
                val previous = mutableFrame.value
                if (previous != null && previous.displayGeneration != parsed.first.displayGeneration) {
                    releaseAll()
                    if (!current(id)) return@synchronized
                }
                mutableFrame.value = parsed.first
                frameValidUntil = started + budget
            }
            delay(maxOf(200L, 200 - (nowMillis() - started)))
        }
    }

    fun sendPointer(value: RemotePointerEvent): Boolean {
        if (!value.valid || (value.action == PointerAction.Scroll && (value.scrollY == 0f || kotlin.math.abs(value.scrollY) > 10))) return false
        val kind = value.action.name.lowercase(Locale.ROOT)
        return enqueue(mapOf("kind" to kind, "x" to value.x, "y" to value.y, "button" to if (value.button == PointerButton.Primary) "primary" else "secondary",
            "scroll" to if (value.action == PointerAction.Scroll) (-value.scrollY * 120).toInt() else 0))
    }

    fun sendKeyboard(value: RemoteKeyboardEvent): Boolean {
        val key = if (value.key.length == 1) value.key.uppercase(Locale.ROOT) else value.key
        if (!value.valid || !allowedKey(key)) return false
        return enqueue(mapOf("kind" to if (value.action == KeyAction.Down) "keyDown" else "keyUp", "key" to key, "x" to 0, "y" to 0))
    }

    private fun enqueue(payload: Map<String, Any?>): Boolean = synchronized(gate) {
        if (!allowInput || !freshFrame() || !foreground || grant == null || mutableStatus.value.task.active) return@synchronized false
        val next = QueuedInput(payload, controlVersion, mutableFrame.value!!.displayGeneration)
        if (payload["kind"] == "move" && queue.peekLast()?.payload?.get("kind") == "move") queue.removeLast()
        if (queue.size >= 100) {
            releaseAll()
            mutableStatus.value = mutableStatus.value.copy(message = "Input queue filled. Resume control after the connection recovers.")
            return@synchronized false
        }
        queue.addLast(next)
        signal.trySend(Unit)
        true
    }

    private fun restartInputWorker(id: Long) { inputJob?.cancel(); inputJob = runtime?.launch { inputLoop(id) } }

    private suspend fun inputLoop(id: Long) {
        for (ignored in signal) {
            while (current(id)) {
                val dispatch = synchronized(gate) {
                    val next = queue.pollFirst() ?: return@synchronized null
                    val session = grant ?: return@synchronized null
                    if (!allowInput || next.version != controlVersion || next.generation != mutableFrame.value?.displayGeneration || !freshFrame() || mutableStatus.value.task.active) return@synchronized null
                    Triple(next.version, session, next.payload + mapOf("sessionId" to session.session.id, "authorityEpoch" to session.session.epoch, "sequence" to ++sequence, "displayGeneration" to next.generation))
                } ?: break
                try {
                    transport.call(workstation.endpoint, RemoteOperation("input", "POST", "Session", dispatch.second.token, dispatch.third))
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    synchronized(gate) {
                        if (dispatch.first == controlVersion) {
                            releaseAll()
                            mutableStatus.value = mutableStatus.value.copy(message = "Input delivery is uncertain. Resume control to continue.")
                        }
                    }
                    break
                }
            }
        }
    }

    private fun freshFrame() = mutableFrame.value != null && nowMillis() < frameValidUntil

    private fun publish(connection: RemoteConnectionState, message: String?) {
        mutableStatus.value = mutableStatus.value.copy(
            connection = connection,
            controller = RemoteController.None,
            mode = RemoteControlMode.Manual,
            controlPending = false,
            message = message,
        )
    }

    private fun readGrant(body: String): Grant = decode {
        val json = JSONObject(body)
        Grant(json.requiredString("sessionToken", 256), readSession(json.getJSONObject("session")))
    }

    private fun readSession(json: JSONObject): Session = decode {
        OffsetDateTime.parse(json.requiredString("expiresAt", 128))
        val epoch = json.getLong("authorityEpoch")
        require(epoch > 0)
        Session(json.requiredString("sessionId", 128), epoch, json.getBoolean("controlling"))
    }

    private fun readAgentStatus(body: String): AgentRuntime = decode {
        val json = JSONObject(body)
        val solidWorks = json.getJSONObject("solidWorks")
        val activeJob = if (json.isNull("activeJob")) null else readJobObject(json.getJSONObject("activeJob"))
        AgentRuntime(
            available = json.getBoolean("agentHostAvailable"),
            executionMode = json.optionalString("executionMode", 64),
            model = json.optionalString("model", 128),
            solidWorksRunning = solidWorks.getBoolean("running"),
            solidWorksAttached = solidWorks.getBoolean("attached"),
            solidWorksVisible = solidWorks.getBoolean("visible"),
            solidWorksVersion = solidWorks.optionalString("version", 128),
            activeDocument = solidWorks.optionalString("activeDocument", 512),
            activeJob = activeJob,
        )
    }

    private fun readJob(body: String): AiTaskState = decode { readJobObject(JSONObject(body)) }

    private fun readJobObject(json: JSONObject): AiTaskState {
        val id = json.requiredString("id", 128)
        UUID.fromString(id)
        val prompt = json.requiredString("prompt", 2000)
        val state = json.requiredString("state", 64)
        val phase = when (state) {
            "Completed" -> AiTaskPhase.Completed
            "Cancelled", "Failed" -> AiTaskPhase.Stopped
            else -> AiTaskPhase.Running
        }
        val message = when (state) {
            "New", "Interpreting" -> "Planning CAD task…"
            "AwaitingClarification" -> "CAD task needs clarification on the PC"
            "AwaitingApproval" -> "CAD plan is awaiting approval on the PC"
            "Approved" -> "CAD plan approved"
            "Executing" -> "Executing CAD task…"
            "Verifying" -> "Verifying CAD changes…"
            "ReadyForReview" -> "CAD changes are ready for review"
            "Completed" -> "CAD task completed"
            "Cancelled" -> "CAD task cancelled"
            "Failed" -> "CAD task failed"
            else -> "CAD task: $state"
        }
        return AiTaskState(id = id, instruction = prompt, phase = phase, message = message)
    }

    private fun readFrame(body: String): Pair<RemoteDisplayFrame, Long> = decode {
        val json = JSONObject(body)
        val width = json.getInt("width")
        val height = json.getInt("height")
        require(width in 1..1600 && height in 1..1600)
        val bytes = Base64.getDecoder().decode(json.requiredString("jpegBytes", 2796204))
        require(bytes.size in 1..2097152)
        val generation = json.getLong("displayGeneration")
        val frameId = json.getLong("frameId")
        require(generation > 0 && frameId > 0)
        val age = json.getLong("ageAtResponseMs")
        require(age in 0..3000)
        val x = json.getDouble("cursorX")
        val y = json.getDouble("cursorY")
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
        OffsetDateTime.parse(json.requiredString("capturedAt", 128))
        RemoteDisplayFrame(width, height, "Live workstation", bytes, generation, frameId, x.toFloat(), y.toFloat()).also { require(validateImage(it)) } to age
    }

    private fun JSONObject.optionalString(name: String, maxLength: Int): String? {
        if (!has(name) || isNull(name)) return null
        return requiredString(name, maxLength)
    }

    private fun <T> decode(action: () -> T): T = try { action() }
    catch (_: Exception) { throw RemoteFailure(0, "invalid_response", "The workstation returned an invalid remote response.") }

    private fun safeMessage(error: Exception) = if (error is RemoteFailure) error.message else "The remote connection failed. Check the Windows control window."
    private fun safeAgentMessage(error: Exception) = if (error is RemoteFailure) error.message else "The CAD Agent request failed. Check the PC agent."

    override fun close() {
        disconnect()
        owned.cancel()
        signal.close()
    }

    fun canSwitchWorkstation(): Boolean = synchronized(gate) { !wanted && connectionJob?.isActive != true }

    companion object {
        private fun allowedKey(key: String) = (key.length == 1 && (key[0] in 'A'..'Z' || key[0] in '0'..'9')) || key in
            setOf("Enter", "Escape", "Tab", "Space", "Backspace", "Left", "Right", "Up", "Down", "Home", "End", "PageUp", "PageDown", "Shift", "Control", "Alt") || key in (1..12).map { "F$it" }

        private fun validateJpeg(frame: RemoteDisplayFrame): Boolean {
            val bytes = frame.jpegBytes ?: return false
            if (bytes.size < 4 || bytes[0] != 0xff.toByte() || bytes[1] != 0xd8.toByte()) return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            return bounds.outWidth == frame.width && bounds.outHeight == frame.height
        }
    }
}
