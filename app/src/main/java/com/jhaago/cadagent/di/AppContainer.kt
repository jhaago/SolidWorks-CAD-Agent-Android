package com.jhaago.cadagent.di

import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.data.FakeCadAgentRepository
import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.FakeRemoteDisplaySource
import com.jhaago.cadagent.remote.input.FakeRemoteInputController

class AppContainer(
    val repository: CadAgentRepository = FakeCadAgentRepository(),
) {
    val remoteSession = FakeRemoteSessionRepository()
    val aiControl = FakeAiControlRepository(remoteSession)
    val remoteDisplay = FakeRemoteDisplaySource()
    val remoteInput = FakeRemoteInputController(remoteSession)
    val remoteDemo = FakeRemoteDemoDriver(remoteSession, aiControl)
}
