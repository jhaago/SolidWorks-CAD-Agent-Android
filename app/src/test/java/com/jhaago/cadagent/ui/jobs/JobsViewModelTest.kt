package com.jhaago.cadagent.ui.jobs

import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.model.AgentAvailability
import com.jhaago.cadagent.model.AgentStatus
import com.jhaago.cadagent.model.CadJob
import com.jhaago.cadagent.model.JobState
import com.jhaago.cadagent.test.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JobsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `jobs are exposed newest first`() {
        val older = job("older", "2026-09-27T09:00:00Z")
        val newer = job("newer", "2026-09-27T09:02:00Z")
        val repository = TestRepository(listOf(older, newer))

        val viewModel = JobsViewModel(repository)

        val state = viewModel.uiState.value
        assertTrue(state is JobsUiState.Content)
        state as JobsUiState.Content
        assertEquals(listOf("newer", "older"), state.jobs.map { it.id })
    }

    @Test
    fun `empty repository produces explicit empty state`() {
        val viewModel = JobsViewModel(TestRepository(emptyList()))

        assertEquals(JobsUiState.Empty, viewModel.uiState.value)
    }

    private fun job(id: String, updated: String): CadJob {
        val timestamp = Instant.parse(updated)
        return CadJob(
            id = id,
            prompt = "Prompt $id",
            state = JobState.AwaitingApproval,
            planValidated = true,
            hasUnresolvedAmbiguity = false,
            ambiguityMessage = null,
            isSimulated = true,
            outputPath = null,
            createdUtc = timestamp,
            updatedUtc = timestamp,
            revisions = emptyList(),
        )
    }

    private class TestRepository(initialJobs: List<CadJob>) : CadAgentRepository {
        override val agentStatus: StateFlow<AgentStatus> = MutableStateFlow(
            AgentStatus(AgentAvailability.Available, true, "SOLIDWORKS 2020 (simulated)"),
        )
        override val jobs: StateFlow<List<CadJob>> = MutableStateFlow(initialJobs)

        override suspend fun createJob(prompt: String): Result<CadJob> = error("unused")
        override suspend fun getJob(jobId: String): Result<CadJob> = error("unused")
        override suspend fun approveJob(jobId: String, revisionId: String): Result<CadJob> = error("unused")
        override suspend fun requestChanges(jobId: String, revisionId: String, instructions: String): Result<CadJob> = error("unused")
        override suspend fun cancelJob(jobId: String): Result<CadJob> = error("unused")
    }
}
