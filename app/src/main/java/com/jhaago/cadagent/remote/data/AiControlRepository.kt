package com.jhaago.cadagent.remote.data

import com.jhaago.cadagent.remote.model.RemoteWorkstationStatus
import kotlinx.coroutines.flow.StateFlow

interface AiControlRepository {
    val status: StateFlow<RemoteWorkstationStatus>
    fun submitTask(instruction: String): String?
    fun stopTask()
    fun approveProtectedAction(id: String): Boolean
    fun rejectProtectedAction(id: String): Boolean
}
