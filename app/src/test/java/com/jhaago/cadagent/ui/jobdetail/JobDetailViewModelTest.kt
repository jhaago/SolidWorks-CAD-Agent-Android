package com.jhaago.cadagent.ui.jobdetail

import com.jhaago.cadagent.data.CadAgentRepository
import com.jhaago.cadagent.data.FakeCadAgentRepository
import com.jhaago.cadagent.model.AgentStatus
import com.jhaago.cadagent.model.CadJob
import com.jhaago.cadagent.model.JobState
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
class JobDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `missing job id produces recoverable not found state`() = runTest {
        val viewModel = JobDetailViewModel("missing-job", FakeCadAgentRepository())

        val state = viewModel.uiState.value
        assertTrue(state is JobDetailUiState.Error)
        state as JobDetailUiState.Error
        assertTrue(state.recoverable)
        assertTrue(state.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun `approval uses displayed revision and updates job state`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create a mounting plate").getOrThrow()
        val viewModel = JobDetailViewModel(created.id, repository)

        val before = viewModel.content()
        val displayedRevision = requireNotNull(before.job.currentRevision).id
        viewModel.approve()

        val after = viewModel.content()
        assertEquals(displayedRevision, before.job.currentRevision?.id)
        assertEquals(JobState.Executing, after.job.state)
        assertFalse(after.canApprove)
        assertNull(after.actionError)
    }

    @Test
    fun `stale approval reports recoverable error and reload gets current revision`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create a pump bracket").getOrThrow()
        val viewModel = JobDetailViewModel(created.id, repository)
        val displayedRevision = requireNotNull(viewModel.content().job.currentRevision)

        val revised = repository.requestChanges(created.id, displayedRevision.id, "Move holes 5 mm outward").getOrThrow()
        viewModel.approve()

        val staleState = viewModel.content()
        assertEquals(displayedRevision.id, staleState.job.currentRevision?.id)
        assertNotNull(staleState.actionError)
        assertTrue(staleState.actionError!!.contains("reload", ignoreCase = true))

        viewModel.reload()
        val refreshed = viewModel.content()
        assertEquals(revised.currentRevision?.id, refreshed.job.currentRevision?.id)
        assertNull(refreshed.actionError)
    }

    @Test
    fun `request changes creates and displays new revision`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create an adaptor plate").getOrThrow()
        val viewModel = JobDetailViewModel(created.id, repository)

        viewModel.requestChanges("  Increase thickness to 12 mm  ")

        val state = viewModel.content()
        assertEquals(2, state.job.currentRevision?.sequence)
        assertEquals("Increase thickness to 12 mm", state.job.currentRevision?.changeInstructions)
        assertEquals(JobState.AwaitingApproval, state.job.state)
        assertTrue(state.canApprove)
    }

    @Test
    fun `duplicate approval action is ignored while first action is in progress`() = runTest {
        val repository = GateActionRepository()
        val created = repository.delegate.createJob("Create a bracket").getOrThrow()
        val viewModel = JobDetailViewModel(created.id, repository)

        viewModel.approve()
        viewModel.approve()

        assertEquals(1, repository.approveCalls)
        assertTrue(viewModel.content().isActionInProgress)

        repository.release.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.approveCalls)
        assertFalse(viewModel.content().isActionInProgress)
        assertEquals(JobState.Executing, viewModel.content().job.state)
    }

    @Test
    fun `cancel follows state rules and disables terminal actions`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create a spacer").getOrThrow()
        val viewModel = JobDetailViewModel(created.id, repository)

        assertTrue(viewModel.content().canCancel)
        viewModel.cancel()

        val state = viewModel.content()
        assertEquals(JobState.Cancelled, state.job.state)
        assertFalse(state.canCancel)
        assertFalse(state.canApprove)
        assertFalse(state.canRequestChanges)
    }

    private fun JobDetailViewModel.content(): JobDetailUiState.Content {
        val state = uiState.value
        assertTrue("Expected content state but was $state", state is JobDetailUiState.Content)
        return state as JobDetailUiState.Content
    }

    private class GateActionRepository : CadAgentRepository {
        val delegate = FakeCadAgentRepository()
        val release = CompletableDeferred<Unit>()
        var approveCalls = 0

        override val agentStatus: StateFlow<AgentStatus> = delegate.agentStatus
        override val jobs: StateFlow<List<CadJob>> = delegate.jobs
        override suspend fun createJob(prompt: String): Result<CadJob> = delegate.createJob(prompt)
        override suspend fun getJob(jobId: String): Result<CadJob> = delegate.getJob(jobId)

        override suspend fun approveJob(jobId: String, revisionId: String): Result<CadJob> {
            approveCalls += 1
            release.await()
            return delegate.approveJob(jobId, revisionId)
        }

        override suspend fun requestChanges(jobId: String, revisionId: String, instructions: String): Result<CadJob> =
            delegate.requestChanges(jobId, revisionId, instructions)
        override suspend fun cancelJob(jobId: String): Result<CadJob> = delegate.cancelJob(jobId)
    }
}
