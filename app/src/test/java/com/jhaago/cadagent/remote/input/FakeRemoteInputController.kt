package com.jhaago.cadagent.remote.input

import com.jhaago.cadagent.remote.data.FakeRemoteSessionRepository
import com.jhaago.cadagent.remote.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordedInput(val sequence: Long, val event: RemoteInputEvent)

/** Records only demo events, never injects OS input. UI-thread confined. */
class FakeRemoteInputController(private val session: FakeRemoteSessionRepository) : RemoteInputController {
    private val mutableEvents = MutableStateFlow<List<RecordedInput>>(emptyList())
    val events = mutableEvents.asStateFlow()
    private var sequence = 0L
    private var epoch = session.authorityEpoch
    private val heldButtons = mutableSetOf<PointerButton>()
    private val heldKeys = mutableSetOf<String>()
    val hasHeldInput: Boolean get() {
        refreshAuthority()
        return heldButtons.isNotEmpty() || heldKeys.isNotEmpty()
    }

    override fun sendPointer(event: RemotePointerEvent): Boolean {
        if (!allowed() || !event.valid) return false
        when (event.action) {
            PointerAction.Down -> heldButtons.add(event.button)
            PointerAction.Up -> heldButtons.remove(event.button)
            else -> Unit
        }
        record(event)
        return true
    }

    override fun sendKeyboard(event: RemoteKeyboardEvent): Boolean {
        if (!allowed() || !event.valid) return false
        if (event.action == KeyAction.Down) heldKeys.add(event.key) else heldKeys.remove(event.key)
        record(event)
        return true
    }

    override fun releaseAll() { heldButtons.clear(); heldKeys.clear() }

    private fun allowed(): Boolean {
        refreshAuthority()
        return session.status.value.let { it.connection == RemoteConnectionState.Connected && it.controller == RemoteController.User }
    }

    private fun refreshAuthority() {
        if (epoch != session.authorityEpoch || session.status.value.controller != RemoteController.User) {
            releaseAll()
            epoch = session.authorityEpoch
        }
    }

    private fun record(event: RemoteInputEvent) {
        mutableEvents.value = (events.value + RecordedInput(++sequence, event)).takeLast(100)
    }
}
