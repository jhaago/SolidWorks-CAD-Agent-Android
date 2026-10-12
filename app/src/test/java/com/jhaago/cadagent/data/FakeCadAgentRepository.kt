package com.jhaago.cadagent.data

import com.jhaago.cadagent.model.AgentAvailability
import com.jhaago.cadagent.model.AgentStatus
import com.jhaago.cadagent.model.CadJob
import com.jhaago.cadagent.model.JobRevision
import com.jhaago.cadagent.model.JobState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCadAgentRepository : CadAgentRepository, FakeJobProgression {
    private val baseTime = Instant.parse("2026-09-27T09:00:00Z")
    private var nextJobNumber = 100
    private var mutationTick = 100L

    private val _agentStatus = MutableStateFlow(
        AgentStatus(
            availability = AgentAvailability.Available,
            solidWorksConnected = true,
            solidWorksVersion = "SOLIDWORKS 2020 (simulated)",
        ),
    )
    override val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _jobs = MutableStateFlow(seedJobs())
    override val jobs: StateFlow<List<CadJob>> = _jobs.asStateFlow()

    override suspend fun createJob(prompt: String): Result<CadJob> {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return Result.failure(CadAgentError.InvalidPrompt())

        val id = "job-${nextJobNumber++}"
        val timestamp = nextTimestamp()
        val revision = JobRevision(
            id = "$id-r1",
            sequence = 1,
            actions = planFor(cleanPrompt),
            createdUtc = timestamp,
        )
        val job = CadJob(
            id = id,
            prompt = cleanPrompt,
            state = JobState.AwaitingApproval,
            planValidated = true,
            hasUnresolvedAmbiguity = false,
            ambiguityMessage = null,
            isSimulated = true,
            outputPath = null,
            createdUtc = timestamp,
            updatedUtc = timestamp,
            revisions = listOf(revision),
        )
        put(job)
        return Result.success(job)
    }

    override suspend fun getJob(jobId: String): Result<CadJob> =
        find(jobId)?.let(Result.Companion::success)
            ?: Result.failure(CadAgentError.NotFound(jobId))

    override suspend fun approveJob(jobId: String, revisionId: String): Result<CadJob> {
        val job = find(jobId) ?: return Result.failure(CadAgentError.NotFound(jobId))
        val currentRevisionId = job.currentRevision?.id
        if (currentRevisionId != revisionId) {
            return Result.failure(CadAgentError.StaleRevision(currentRevisionId, revisionId))
        }
        if (job.state != JobState.AwaitingApproval || !job.planValidated || job.hasUnresolvedAmbiguity) {
            return Result.failure(CadAgentError.InvalidState(job.id, job.state))
        }

        return Result.success(update(job, state = JobState.Executing))
    }

    override suspend fun requestChanges(
        jobId: String,
        revisionId: String,
        instructions: String,
    ): Result<CadJob> {
        val job = find(jobId) ?: return Result.failure(CadAgentError.NotFound(jobId))
        val currentRevision = job.currentRevision
        if (currentRevision?.id != revisionId) {
            return Result.failure(CadAgentError.StaleRevision(currentRevision?.id, revisionId))
        }
        if (job.state != JobState.AwaitingApproval) {
            return Result.failure(CadAgentError.InvalidState(job.id, job.state))
        }
        val cleanInstructions = instructions.trim()
        if (cleanInstructions.isEmpty()) return Result.failure(CadAgentError.InvalidInstructions())

        val timestamp = nextTimestamp()
        val sequence = currentRevision.sequence + 1
        val revisedPlan = JobRevision(
            id = "$jobId-r$sequence",
            sequence = sequence,
            actions = currentRevision.actions + "Apply requested revision: $cleanInstructions",
            createdUtc = timestamp,
            changeInstructions = cleanInstructions,
        )
        val revisedJob = job.copy(
            state = JobState.AwaitingApproval,
            planValidated = true,
            hasUnresolvedAmbiguity = false,
            ambiguityMessage = null,
            updatedUtc = timestamp,
            revisions = job.revisions + revisedPlan,
        )
        put(revisedJob)
        return Result.success(revisedJob)
    }

    override suspend fun cancelJob(jobId: String): Result<CadJob> {
        val job = find(jobId) ?: return Result.failure(CadAgentError.NotFound(jobId))
        if (job.state in terminalStates) {
            return Result.failure(CadAgentError.InvalidState(job.id, job.state))
        }
        return Result.success(update(job, state = JobState.Cancelled))
    }

    override suspend fun advanceJob(jobId: String): Result<CadJob> {
        val job = find(jobId) ?: return Result.failure(CadAgentError.NotFound(jobId))
        val nextState = when (job.state) {
            JobState.Executing -> JobState.Verifying
            JobState.Verifying -> JobState.ReadyForReview
            JobState.ReadyForReview -> JobState.Completed
            else -> return Result.failure(CadAgentError.InvalidState(job.id, job.state))
        }
        val outputPath = if (nextState == JobState.Completed) {
            "Workspace/${job.id}.SLDPRT"
        } else {
            job.outputPath
        }
        return Result.success(update(job, state = nextState, outputPath = outputPath))
    }

    private fun update(job: CadJob, state: JobState, outputPath: String? = job.outputPath): CadJob {
        val updated = job.copy(
            state = state,
            outputPath = outputPath,
            updatedUtc = nextTimestamp(),
        )
        put(updated)
        return updated
    }

    private fun find(jobId: String): CadJob? = _jobs.value.firstOrNull { it.id == jobId }

    private fun put(job: CadJob) {
        _jobs.value = (_jobs.value.filterNot { it.id == job.id } + job)
            .sortedByDescending { it.updatedUtc }
    }

    private fun nextTimestamp(): Instant = baseTime.plusSeconds(mutationTick++)

    private fun planFor(prompt: String): List<String> = listOf(
        "Create or open the native SOLIDWORKS part required for: $prompt",
        "Create the required reference sketches and dimensions",
        "Build the requested native feature set",
        "Rebuild and verify the resulting geometry",
        "Save the native SOLIDWORKS model",
    )

    private fun seedJobs(): List<CadJob> {
        val seeds = listOf(
            seed("seed-cancelled", "Cancelled spacer revision", JobState.Cancelled, 1),
            seed("seed-failed", "Bracket rebuild investigation", JobState.Failed, 2),
            seed(
                "seed-clarification",
                "Create a mounting plate using the existing hole pattern",
                JobState.AwaitingClarification,
                3,
                planValidated = false,
                ambiguityMessage = "The existing hole-pattern reference is not available in this mock job.",
            ),
            seed("seed-completed", "100 x 60 x 10 mm plate with centred hole", JobState.Completed, 4),
            seed("seed-executing", "Pump mounting bracket revision", JobState.Executing, 5),
            seed("seed-approval", "Motor adaptor plate", JobState.AwaitingApproval, 6),
        )
        return seeds.sortedByDescending { it.updatedUtc }
    }

    private fun seed(
        id: String,
        prompt: String,
        state: JobState,
        second: Long,
        planValidated: Boolean = true,
        ambiguityMessage: String? = null,
    ): CadJob {
        val timestamp = baseTime.plusSeconds(second)
        val revision = JobRevision(
            id = "$id-r1",
            sequence = 1,
            actions = planFor(prompt),
            createdUtc = timestamp,
        )
        return CadJob(
            id = id,
            prompt = prompt,
            state = state,
            planValidated = planValidated,
            hasUnresolvedAmbiguity = ambiguityMessage != null,
            ambiguityMessage = ambiguityMessage,
            isSimulated = true,
            outputPath = if (state == JobState.Completed) "Workspace/$id.SLDPRT" else null,
            createdUtc = timestamp,
            updatedUtc = timestamp,
            revisions = listOf(revision),
        )
    }

    private companion object {
        val terminalStates = setOf(JobState.Completed, JobState.Failed, JobState.Cancelled)
    }
}
