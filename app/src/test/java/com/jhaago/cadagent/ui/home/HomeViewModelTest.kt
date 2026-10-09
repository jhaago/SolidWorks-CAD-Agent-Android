package com.jhaago.cadagent.ui.home

import com.jhaago.cadagent.data.FakeCadAgentRepository
import com.jhaago.cadagent.model.AgentAvailability
import com.jhaago.cadagent.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `home exposes agent status solidworks status and three most recent jobs`() {
        val repository = FakeCadAgentRepository()
        val viewModel = HomeViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Content)
        state as HomeUiState.Content
        assertEquals(AgentAvailability.Available, state.agentStatus.availability)
        assertEquals(true, state.agentStatus.solidWorksConnected)
        assertTrue(state.agentStatus.solidWorksVersion?.contains("SOLIDWORKS 2020") == true)
        assertEquals(3, state.recentJobs.size)
        assertEquals(state.recentJobs.sortedByDescending { it.updatedUtc }, state.recentJobs)
    }
}
