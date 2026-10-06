package com.jhaago.cadagent.di

import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.RemoteDisplaySource
import com.jhaago.cadagent.remote.input.RemoteInputController
import com.jhaago.cadagent.remote.live.*
import com.jhaago.cadagent.remote.model.RemoteConnectionState
import com.jhaago.cadagent.remote.model.RemoteWorkstationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteAdapters(val revision: Long, val session: RemoteSessionRepository, val ai: AiControlRepository,
    val display: RemoteDisplaySource, val input: RemoteInputController, val driver: LiveConnectionDriver? = null)

private class UnpairedRemoteSessionRepository : RemoteSessionRepository {
    private val mutableStatus = MutableStateFlow(RemoteWorkstationStatus(workstationName = "No workstation paired"))
    override val status = mutableStatus.asStateFlow()
    override fun connect(): Long? = null
    override fun disconnect() = Unit
    override fun setMode(mode: com.jhaago.cadagent.remote.model.RemoteControlMode) = Unit
    override fun takeControl() = Unit
}

private class UnavailableAiControlRepository(session: RemoteSessionRepository) : AiControlRepository {
    override val status = session.status
    override fun submitTask(instruction: String): String? = null
    override fun stopTask() = Unit
    override fun approveProtectedAction(id: String): Boolean = false
    override fun rejectProtectedAction(id: String): Boolean = false
}

private class EmptyRemoteDisplaySource : RemoteDisplaySource {
    override val frame = MutableStateFlow<com.jhaago.cadagent.remote.display.RemoteDisplayFrame?>(null).asStateFlow()
}

private object UnavailableRemoteInputController : RemoteInputController {
    override fun sendPointer(event: com.jhaago.cadagent.remote.input.RemotePointerEvent) = false
    override fun sendKeyboard(event: com.jhaago.cadagent.remote.input.RemoteKeyboardEvent) = false
    override fun releaseAll() = Unit
}

class AppContainer {
    private val unpairedSession = UnpairedRemoteSessionRepository()
    private val selected = MutableStateFlow(RemoteAdapters(
        0, unpairedSession, UnavailableAiControlRepository(unpairedSession), EmptyRemoteDisplaySource(), UnavailableRemoteInputController,
    ))
    val remoteAdapters = selected.asStateFlow()
    var remoteSettings: WorkstationSettingsController? = null
        private set

    fun canSwitchRemote() = selected.value.driver?.canSwitchWorkstation()
        ?: (selected.value.session.status.value.connection == RemoteConnectionState.Disconnected)

    fun selectLive(driver: LiveConnectionDriver): Boolean {
        if (!canSwitchRemote()) return false
        selected.value.driver?.close()
        selected.value = RemoteAdapters(
            selected.value.revision + 1,
            LiveRemoteSessionRepository(driver),
            LiveAiControlRepository(driver),
            LiveRemoteDisplaySource(driver),
            LiveRemoteInputController(driver),
            driver = driver,
        )
        return true
    }

    fun selectUnpaired(): Boolean {
        if (!canSwitchRemote()) return false
        selected.value.driver?.close()
        val session = UnpairedRemoteSessionRepository()
        selected.value = RemoteAdapters(selected.value.revision + 1, session, UnavailableAiControlRepository(session), EmptyRemoteDisplaySource(), UnavailableRemoteInputController)
        return true
    }

    fun configureRemoteSettings(scope: CoroutineScope, transport: RemoteTransport, store: RemoteCredentialStore,
        initialOrigin: String = "", saveOrigin: (String) -> Unit = {}) {
        remoteSettings = WorkstationSettingsController(scope, this, transport, store, initialOrigin, saveOrigin)
    }

    fun close() {
        remoteSettings?.cancelPairing()
        selected.value.driver?.close()
        selected.value.input.releaseAll()
        selected.value.session.disconnect()
    }
}
