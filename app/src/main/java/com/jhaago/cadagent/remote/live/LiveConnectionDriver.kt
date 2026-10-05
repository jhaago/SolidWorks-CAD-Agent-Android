package com.jhaago.cadagent.remote.live

import android.graphics.BitmapFactory
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import java.time.OffsetDateTime
import java.util.ArrayDeque
import java.util.Base64
import java.util.Locale
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
    private var lastFrameAt = Long.MIN_VALUE
    private var renewAt = Long.MAX_VALUE
    private var grant: Grant? = null
    private var connectionJob: Job? = null
    private var controlJob: Job? = null
    private var inputJob: Job? = null
    private var runtime: CoroutineScope? = null
    private val queue = ArrayDeque<QueuedInput>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private data class Session(val id: String, val epoch: Long, val controlling: Boolean)
    private class Grant(val token: String, val session: Session) { override fun toString() = "Remote session grant" }
    private data class QueuedInput(val payload: Map<String, Any?>, val version: Long, val generation: Long)

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
                        grant = opened; renewAt = nowMillis() + 240000; allowInput = false
                        publish(RemoteConnectionState.Connected, message = "Viewing only. Resume control when ready.")
                    }
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
                        invalidateAuthority(); grant = null; runtime = null; mutableFrame.value = null
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
        attempt++; connectionJob?.cancel(); connectionJob = null; controlJob?.cancel()
        val old = grant
        invalidateAuthority(); grant = null; runtime = null; mutableFrame.value = null
        publish(RemoteConnectionState.Disconnected, message = if (keepWanted && wanted && !foreground) "Paused while Remote is not visible." else null)
        old?.let { cleanup(it, "session/close") }
    }
    fun resumeControl() = synchronized(gate) {
        val captured = grant ?: return@synchronized
        if (!freshFrame() || !foreground || mutableStatus.value.controlPending) return@synchronized
        invalidateAuthority()
        val version = controlVersion; val id = attempt
        mutableStatus.value = mutableStatus.value.copy(controlPending = true, message = "Requesting control…")
        controlJob = work.launch {
            try {
                val session = readSession(JSONObject(transport.call(workstation.endpoint, RemoteOperation("session/resume", "POST", "Session", captured.token)).body))
                synchronized(gate) {
                    if (!current(id) || version != controlVersion || grant?.token != captured.token) return@launch
                    if (!freshFrame() || !session.controlling) { releaseAll(); return@launch }
                    grant = Grant(captured.token, session); sequence = 0; allowInput = true
                    mutableStatus.value = mutableStatus.value.copy(controller = RemoteController.User, controlPending = false, message = "Manual control active")
                    restartInputWorker(id)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { synchronized(gate) { if (version == controlVersion) { releaseAll(); mutableStatus.value = mutableStatus.value.copy(controlPending = false, message = safeMessage(error)) } } }
        }
    }
    fun releaseAll() = synchronized(gate) {
        val old = grant
        val shouldRelease = allowInput || mutableStatus.value.controlPending || queue.isNotEmpty()
        invalidateAuthority()
        if (mutableStatus.value.connection == RemoteConnectionState.Connected) mutableStatus.value = mutableStatus.value.copy(controller = RemoteController.None, controlPending = false, message = "Viewing only. Resume control when ready.")
        if (shouldRelease && old != null) {
            // End the entire old lease before attempting network cleanup. A failed
            // release must never be kept alive by otherwise healthy heartbeats.
            cleanup(old, "session/release")
            stopConnection(keepWanted = true)
            if (foreground && wanted) startConnection()
        }
    }
    private fun invalidateAuthority() {
        allowInput = false; controlVersion++; sequence = 0; queue.clear(); inputJob?.cancel(); controlJob?.cancel()
    }
    private fun cleanup(old: Grant, route: String) {
        scope.launch {
            try { withTimeout(2500) { transport.call(workstation.endpoint, RemoteOperation(route, "POST", "Session", old.token,
                if (route == "session/release") mapOf("authorityEpoch" to old.session.epoch) else emptyMap())) } }
            catch (error: CancellationException) { throw error }
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
                        releaseAll(); return@synchronized
                    }
                    grant = Grant(captured.token, state)
                }
                if (mutableFrame.value != null && !freshFrame()) {
                    releaseAll(); mutableFrame.value = null
                    mutableStatus.value = mutableStatus.value.copy(message = "Desktop image is stale. Waiting for a current view.")
                }
            }
        }
    }
    private suspend fun frameLoop(id: Long) {
        while (current(id)) {
            val started = nowMillis()
            val captured = synchronized(gate) { grant } ?: return
            val response = try { transport.call(workstation.endpoint, RemoteOperation("display/frame", "GET", "Session", captured.token)) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    // Renewal rotates the token while an independent frame request can still
                    // be in flight. Its failure says nothing about the replacement session.
                    if (synchronized(gate) { current(id) && grant?.token != captured.token }) continue
                    throw error
                }
            val parsed = readFrame(response.body)
            synchronized(gate) {
                if (!current(id) || grant?.token != captured.token) return@synchronized
                val previous = mutableFrame.value
                if (previous != null && previous.displayGeneration != parsed.displayGeneration) {
                    releaseAll()
                    if (!current(id)) return@synchronized
                }
                mutableFrame.value = parsed; lastFrameAt = nowMillis()
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
        if (!allowInput || !freshFrame() || !foreground || grant == null) return@synchronized false
        val next = QueuedInput(payload, controlVersion, mutableFrame.value!!.displayGeneration)
        if (payload["kind"] == "move" && queue.peekLast()?.payload?.get("kind") == "move") queue.removeLast()
        if (queue.size >= 100) { releaseAll(); mutableStatus.value = mutableStatus.value.copy(message = "Input queue filled. Resume control after the connection recovers."); return@synchronized false }
        queue.addLast(next); signal.trySend(Unit); true
    }
    private fun restartInputWorker(id: Long) { inputJob?.cancel(); inputJob = runtime?.launch { inputLoop(id) } }
    private suspend fun inputLoop(id: Long) {
        for (ignored in signal) {
            while (current(id)) {
                val dispatch = synchronized(gate) {
                    val next = queue.pollFirst() ?: return@synchronized null
                    val session = grant ?: return@synchronized null
                    if (!allowInput || next.version != controlVersion || next.generation != mutableFrame.value?.displayGeneration || !freshFrame()) return@synchronized null
                    Triple(next.version, session, next.payload + mapOf("sessionId" to session.session.id, "authorityEpoch" to session.session.epoch, "sequence" to ++sequence, "displayGeneration" to next.generation))
                } ?: break
                try { transport.call(workstation.endpoint, RemoteOperation("input", "POST", "Session", dispatch.second.token, dispatch.third)) }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { synchronized(gate) { if (dispatch.first == controlVersion) { releaseAll(); mutableStatus.value = mutableStatus.value.copy(message = "Input delivery is uncertain. Resume control to continue.") } }; break }
            }
        }
    }
    private fun freshFrame() = mutableFrame.value != null && nowMillis() - lastFrameAt < 3000
    private fun publish(connection: RemoteConnectionState, message: String?) {
        mutableStatus.value = mutableStatus.value.copy(connection = connection, controller = RemoteController.None, mode = RemoteControlMode.Manual, controlPending = false, message = message)
    }
    private fun readGrant(body: String): Grant = decode {
        val json = JSONObject(body); Grant(json.requiredString("sessionToken", 256), readSession(json.getJSONObject("session")))
    }
    private fun readSession(json: JSONObject): Session = decode {
        OffsetDateTime.parse(json.requiredString("expiresAt", 128))
        val epoch = json.getLong("authorityEpoch"); require(epoch > 0)
        Session(json.requiredString("sessionId", 128), epoch, json.getBoolean("controlling"))
    }
    private fun readFrame(body: String): RemoteDisplayFrame = decode {
        val json = JSONObject(body); val width = json.getInt("width"); val height = json.getInt("height")
        require(width in 1..1600 && height in 1..1600)
        val bytes = Base64.getDecoder().decode(json.requiredString("jpegBytes", 2796204)); require(bytes.size in 1..2097152)
        val generation = json.getLong("displayGeneration"); val frameId = json.getLong("frameId"); require(generation > 0 && frameId > 0)
        val x = json.getDouble("cursorX"); val y = json.getDouble("cursorY"); require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
        OffsetDateTime.parse(json.requiredString("capturedAt", 128))
        RemoteDisplayFrame(width, height, "Live workstation", bytes, generation, frameId, x.toFloat(), y.toFloat()).also { require(validateImage(it)) }
    }
    private fun <T> decode(action: () -> T): T = try { action() } catch (_: Exception) { throw RemoteFailure(0, "invalid_response", "The workstation returned an invalid remote response.") }
    private fun safeMessage(error: Exception) = if (error is RemoteFailure) error.message else "The remote connection failed. Check the Windows control window."
    override fun close() { disconnect(); owned.cancel(); signal.close() }
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
