package com.jhaago.cadagent.data

import com.jhaago.cadagent.model.JobState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCadAgentRepositoryTest {
    @Test
    fun `seeded jobs cover representative lifecycle states and are newest first`() {
        val repository = FakeCadAgentRepository()

        val jobs = repository.jobs.value
        val states = jobs.map { it.state }.toSet()

        assertTrue(states.contains(JobState.AwaitingApproval))
        assertTrue(states.contains(JobState.Executing))
        assertTrue(states.contains(JobState.Completed))
        assertTrue(states.contains(JobState.AwaitingClarification))
        assertTrue(states.contains(JobState.Failed))
        assertTrue(states.contains(JobState.Cancelled))
        assertEquals(jobs.sortedByDescending { it.updatedUtc }, jobs)
    }

    @Test
    fun `blank prompt is rejected without creating a job`() = runTest {
        val repository = FakeCadAgentRepository()
        val before = repository.jobs.value.size

        val result = repository.createJob("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CadAgentError.InvalidPrompt)
        assertEquals(before, repository.jobs.value.size)
    }

    @Test
    fun `create job trims prompt and creates validated approval revision`() = runTest {
        val repository = FakeCadAgentRepository()

        val job = repository.createJob("  Create a 100 x 60 x 10 mm plate  ").getOrThrow()

        assertEquals("Create a 100 x 60 x 10 mm plate", job.prompt)
        assertEquals(JobState.AwaitingApproval, job.state)
        assertTrue(job.planValidated)
        assertTrue(job.isSimulated)
        assertEquals(1, job.currentRevision?.sequence)
        assertTrue(job.currentRevision?.actions?.isNotEmpty() == true)
    }

    @Test
    fun `approval is revision bound and progression is explicit`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create a mounting plate").getOrThrow()
        val revisionId = requireNotNull(created.currentRevision).id

        val stale = repository.approveJob(created.id, "stale-revision")
        assertTrue(stale.exceptionOrNull() is CadAgentError.StaleRevision)
        assertEquals(JobState.AwaitingApproval, repository.getJob(created.id).getOrThrow().state)

        val approved = repository.approveJob(created.id, revisionId).getOrThrow()
        assertEquals(JobState.Executing, approved.state)

        val verifying = repository.advanceJob(created.id).getOrThrow()
        assertEquals(JobState.Verifying, verifying.state)
        val ready = repository.advanceJob(created.id).getOrThrow()
        assertEquals(JobState.ReadyForReview, ready.state)
        val completed = repository.advanceJob(created.id).getOrThrow()
        assertEquals(JobState.Completed, completed.state)
    }

    @Test
    fun `request changes retains old revision and creates a new current revision`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create a pump bracket").getOrThrow()
        val firstRevision = requireNotNull(created.currentRevision)

        val revised = repository.requestChanges(
            created.id,
            firstRevision.id,
            "  Move the mounting holes 5 mm outward  ",
        ).getOrThrow()

        assertEquals(JobState.AwaitingApproval, revised.state)
        assertEquals(2, revised.revisions.size)
        assertEquals(firstRevision.id, revised.revisions.first().id)
        assertEquals(2, revised.currentRevision?.sequence)
        assertEquals("Move the mounting holes 5 mm outward", revised.currentRevision?.changeInstructions)

        val stale = repository.requestChanges(created.id, firstRevision.id, "Another change")
        assertTrue(stale.exceptionOrNull() is CadAgentError.StaleRevision)
    }

    @Test
    fun `cancellation updates nonterminal job and terminal job cannot be cancelled again`() = runTest {
        val repository = FakeCadAgentRepository()
        val created = repository.createJob("Create a spacer").getOrThrow()

        val cancelled = repository.cancelJob(created.id).getOrThrow()
        assertEquals(JobState.Cancelled, cancelled.state)

        val secondCancel = repository.cancelJob(created.id)
        assertTrue(secondCancel.exceptionOrNull() is CadAgentError.InvalidState)
    }

    @Test
    fun `unknown job returns recoverable not found error`() = runTest {
        val repository = FakeCadAgentRepository()

        val result = repository.getJob("missing-job")

        assertTrue(result.exceptionOrNull() is CadAgentError.NotFound)
    }
}
