package com.jhaago.cadagent.ui.newjob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.data.CadAgentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewJobUiState(
    val prompt: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val submittedJobId: String? = null,
)

class NewJobViewModel(
    private val repository: CadAgentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewJobUiState())
    val uiState: StateFlow<NewJobUiState> = _uiState.asStateFlow()

    fun onPromptChanged(prompt: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(prompt = prompt, errorMessage = null) }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return
        val cleanPrompt = current.prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a CAD request before submitting.") }
            return
        }

        _uiState.value = current.copy(isSubmitting = true, errorMessage = null, submittedJobId = null)
        viewModelScope.launch {
            repository.createJob(cleanPrompt)
                .onSuccess { job ->
                    _uiState.update {
                        it.copy(
                            prompt = cleanPrompt,
                            isSubmitting = false,
                            errorMessage = null,
                            submittedJobId = job.id,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Unable to create the CAD job.",
                        )
                    }
                }
        }
    }

    fun consumeSubmittedJob() {
        _uiState.update { it.copy(submittedJobId = null) }
    }
}
