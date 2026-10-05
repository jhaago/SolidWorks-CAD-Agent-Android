package com.jhaago.cadagent.di

import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.data.FakeCadAgentRepository
import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.FakeRemoteDisplaySource
import com.jhaago.cadagent.remote.display.RemoteDisplaySource
import com.jhaago.cadagent.remote.input.FakeRemoteInputController
import com.jhaago.cadagent.remote.input.RemoteInputController
import com.jhaago.cadagent.remote.live.*
import com.jhaago.cadagent.remote.model.RemoteConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteAdapters(val revision: Long, val session: RemoteSessionRepository, val ai: AiControlRepository,
    val display: RemoteDisplaySource, val input: RemoteInputController, val demo: RemoteDemoDriver? = null,
    val driver: LiveConnectionDriver? = null)

class AppContainer(
    val repository: CadAgentRepository = FakeCadAgentRepository(),
) {
    val remoteSession = FakeRemoteSessionRepository()
    val aiControl = FakeAiControlRepository(remoteSession)
    val remoteDisplay = FakeRemoteDisplaySource()
    val remoteInput = FakeRemoteInputController(remoteSession)
    val remoteDemo = FakeRemoteDemoDriver(remoteSession, aiControl)
    private val selected = MutableStateFlow(RemoteAdapters(0, remoteSession, aiControl, remoteDisplay, remoteInput, remoteDemo))
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

    fun selectDemo(): Boolean {
        if (!canSwitchRemote()) return false
        selected.value.driver?.close()
        selected.value = RemoteAdapters(selected.value.revision + 1, remoteSession, aiControl, remoteDisplay, remoteInput, remoteDemo)
        return true
    }

    fun configureRemoteSettings(scope: CoroutineScope, transport: RemoteTransport, store: RemoteCredentialStore,
        initialOrigin: String = "", saveOrigin: (String) -> Unit = {}) {
        remoteSettings = WorkstationSettingsController(scope, this, transport, store, initialOrigin, saveOrigin)
    }

    fun close() {
        remoteSettings?.cancelPairing()
        selected.value.driver?.close()
        remoteInput.releaseAll()
        remoteSession.disconnect()
    }
}
