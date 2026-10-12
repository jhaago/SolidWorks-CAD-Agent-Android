package com.jhaago.cadagent.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.model.CadJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface JobsUiState {
    data object Loading : JobsUiState
    data object Empty : JobsUiState
    data class Content(val jobs: List<CadJob>) : JobsUiState
    data class Error(val message: String) : JobsUiState
}

class JobsViewModel(repository: CadAgentRepository) : ViewModel() {
    val uiState: StateFlow<JobsUiState> = repository.jobs
        .map { jobs ->
            val sorted = jobs.sortedByDescending { it.updatedUtc }
            if (sorted.isEmpty()) JobsUiState.Empty else JobsUiState.Content(sorted)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = JobsUiState.Loading,
        )
}
