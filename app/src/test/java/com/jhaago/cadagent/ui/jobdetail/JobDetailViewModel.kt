package com.jhaago.cadagent.ui.jobdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.data.CadAgentError
import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.model.CadJob
import com.jhaago.cadagent.model.JobState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface JobDetailUiState {
    data object Loading : JobDetailUiState

    data class Error(
        val message: String,
        val recoverable: Boolean,
    ) : JobDetailUiState

    data class Content(
        val job: CadJob,
        val isActionInProgress: Boolean = false,
        val actionError: String? = null,
    ) : JobDetailUiState {
        val canApprove: Boolean
            get() = !isActionInProgress &&
                job.state == JobState.AwaitingApproval &&
                job.planValidated &&
                !job.hasUnresolvedAmbiguity &&
                job.currentRevision != null

        val canRequestChanges: Boolean
            get() = !isActionInProgress &&
                job.state == JobState.AwaitingApproval &&
                job.currentRevision != null

        val canCancel: Boolean
            get() = !isActionInProgress && job.state !in terminalStates

        private companion object {
            val terminalStates = setOf(JobState.Completed, JobState.Failed, JobState.Cancelled)
        }
    }
}

class JobDetailViewModel(
    private val jobId: String,
    private val repository: CadAgentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<JobDetailUiState>(JobDetailUiState.Loading)
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        if (jobId.isBlank()) {
            _uiState.value = JobDetailUiState.Error("The CAD job ID is missing.", recoverable = true)
            return
        }

        _uiState.value = JobDetailUiState.Loading
        viewModelScope.launch {
            repository.getJob(jobId)
                .onSuccess { job -> _uiState.value = JobDetailUiState.Content(job) }
                .onFailure { error ->
                    _uiState.value = JobDetailUiState.Error(
                        message = error.message ?: "Unable to load the CAD job.",
                        recoverable = true,
                    )
                }
        }
    }

    fun approve() {
        val content = _uiState.value as? JobDetailUiState.Content ?: return
        if (!content.canApprove) return
        val revisionId = content.job.currentRevision?.id ?: return
        runAction(content) { repository.approveJob(content.job.id, revisionId) }
    }

    fun requestChanges(instructions: String) {
        val content = _uiState.value as? JobDetailUiState.Content ?: return
        if (!content.canRequestChanges) return
        val cleanInstructions = instructions.trim()
        if (cleanInstructions.isEmpty()) {
            _uiState.value = content.copy(actionError = "Enter the changes you want before submitting.")
            return
        }
        val revisionId = content.job.currentRevision?.id ?: return
        runAction(content) {
            repository.requestChanges(content.job.id, revisionId, cleanInstructions)
        }
    }

    fun cancel() {
        val content = _uiState.value as? JobDetailUiState.Content ?: return
        if (!content.canCancel) return
        runAction(content) { repository.cancelJob(content.job.id) }
    }

    private fun runAction(
        displayedContent: JobDetailUiState.Content,
        action: suspend () -> Result<CadJob>,
    ) {
        if (displayedContent.isActionInProgress) return
        val busyState = displayedContent.copy(isActionInProgress = true, actionError = null)
        _uiState.value = busyState
        viewModelScope.launch {
            action()
                .onSuccess { job -> _uiState.value = JobDetailUiState.Content(job) }
                .onFailure { error ->
                    _uiState.value = displayedContent.copy(
                        isActionInProgress = false,
                        actionError = actionErrorMessage(error),
                    )
                }
        }
    }

    private fun actionErrorMessage(error: Throwable): String = when (error) {
        is CadAgentError.StaleRevision ->
            "This plan changed after it was displayed. Reload the current revision before trying again."
        is CadAgentError.NotFound -> "This CAD job could not be found. Reload or return to the job list."
        else -> error.message ?: "The CAD job action could not be completed."
    }
}
