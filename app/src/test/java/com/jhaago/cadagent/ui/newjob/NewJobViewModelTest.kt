package com.jhaago.cadagent.ui.newjob

import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.data.FakeCadAgentRepository
import com.jhaago.cadagent.model.AgentStatus
import com.jhaago.cadagent.model.CadJob
import com.jhaago.cadagent.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewJobViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `blank prompt is rejected before repository submission`() = runTest {
        val repository = GateCreateRepository()
        val viewModel = NewJobViewModel(repository)

        viewModel.onPromptChanged("   ")
        viewModel.submit()

        assertEquals(0, repository.createCalls)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.submittedJobId)
    }

    @Test
    fun `successful submission exposes created job id for navigation`() = runTest {
        val repository = FakeCadAgentRepository()
        val viewModel = NewJobViewModel(repository)

        viewModel.onPromptChanged("Create a 100 x 60 x 10 mm plate")
        viewModel.submit()

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitting)
        assertNull(state.errorMessage)
        assertTrue(state.submittedJobId?.startsWith("job-") == true)
        assertEquals("Create a 100 x 60 x 10 mm plate", state.prompt)
    }

    @Test
    fun `duplicate submit is ignored while submission is in progress`() = runTest {
        val repository = GateCreateRepository()
        val viewModel = NewJobViewModel(repository)

        viewModel.onPromptChanged("Create a spacer")
        viewModel.submit()
        viewModel.submit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.createCalls)

        repository.release.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.createCalls)
        assertNotNull(viewModel.uiState.value.submittedJobId)
    }

    private class GateCreateRepository : CadAgentRepository {
        private val delegate = FakeCadAgentRepository()
        val release = CompletableDeferred<Unit>()
        var createCalls = 0

        override val agentStatus: StateFlow<AgentStatus> = delegate.agentStatus
        override val jobs: StateFlow<List<CadJob>> = delegate.jobs

        override suspend fun createJob(prompt: String): Result<CadJob> {
            createCalls += 1
            release.await()
            return delegate.createJob(prompt)
        }

        override suspend fun getJob(jobId: String): Result<CadJob> = delegate.getJob(jobId)
        override suspend fun approveJob(jobId: String, revisionId: String): Result<CadJob> =
            delegate.approveJob(jobId, revisionId)
        override suspend fun requestChanges(jobId: String, revisionId: String, instructions: String): Result<CadJob> =
            delegate.requestChanges(jobId, revisionId, instructions)
        override suspend fun cancelJob(jobId: String): Result<CadJob> = delegate.cancelJob(jobId)
    }
}
