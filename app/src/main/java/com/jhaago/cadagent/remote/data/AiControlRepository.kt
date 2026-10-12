package com.jhaago.cadagent.remote.data

import com.jhaago.cadagent.remote.model.RemoteWorkstationStatus
import kotlinx.coroutines.flow.StateFlow

interface AiControlRepository {
    val status: StateFlow<RemoteWorkstationStatus>
    fun submitTask(instruction: String): String?
    fun submitTaskWithImage(instruction: String, photo: com.jhaago.cadagent.remote.model.CadPhoto): String? = null
    fun approvePlan(): Boolean = false
    fun requestChanges(instructions: String): Boolean = false
    fun completeTask(): Boolean = false
    suspend fun downloadArtifact(): com.jhaago.cadagent.remote.model.AiArtifact? = null
    fun stopTask()
    fun approveProtectedAction(id: String): Boolean
    fun rejectProtectedAction(id: String): Boolean
}
