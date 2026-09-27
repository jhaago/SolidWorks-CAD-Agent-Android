package com.jhaago.cadagent.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.model.AgentStatus
import com.jhaago.cadagent.model.CadJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val agentStatus: AgentStatus,
        val recentJobs: List<CadJob>,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(repository: CadAgentRepository) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.agentStatus,
        repository.jobs,
    ) { status, jobs ->
        HomeUiState.Content(
            agentStatus = status,
            recentJobs = jobs.sortedByDescending { it.updatedUtc }.take(3),
        ) as HomeUiState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState.Loading,
    )
}
