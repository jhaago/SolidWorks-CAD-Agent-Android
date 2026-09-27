package com.jhaago.cadagent.data

import com.jhaago.cadagent.model.AgentStatus
import com.jhaago.cadagent.model.CadJob
import kotlinx.coroutines.flow.StateFlow

interface CadAgentRepository {
    val agentStatus: StateFlow<AgentStatus>
    val jobs: StateFlow<List<CadJob>>

    suspend fun createJob(prompt: String): Result<CadJob>
    suspend fun getJob(jobId: String): Result<CadJob>
    suspend fun approveJob(jobId: String, revisionId: String): Result<CadJob>
    suspend fun requestChanges(jobId: String, revisionId: String, instructions: String): Result<CadJob>
    suspend fun cancelJob(jobId: String): Result<CadJob>
}
