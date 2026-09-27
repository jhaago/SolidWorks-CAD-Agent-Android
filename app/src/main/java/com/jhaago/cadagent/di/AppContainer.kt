package com.jhaago.cadagent.di

import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.data.FakeCadAgentRepository

class AppContainer(
    val repository: CadAgentRepository = FakeCadAgentRepository(),
)
