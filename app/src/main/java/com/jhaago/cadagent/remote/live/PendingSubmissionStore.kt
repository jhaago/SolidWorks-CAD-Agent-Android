package com.jhaago.cadagent.remote.live

import java.util.UUID

data class SubmissionScope(val origin: String, val deviceId: String)
data class PendingSubmission(val id: String, val cancelRequested: Boolean) {
    init {
        require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        require(UUID.fromString(id) != UUID(0, 0))
    }
}
class SubmissionStorageException : Exception("Submission tracking storage is unavailable. Check the workstation before sending another task.")

interface PendingSubmissionStore {
    suspend fun read(scope: SubmissionScope): PendingSubmission?
    suspend fun write(scope: SubmissionScope, submission: PendingSubmission)
    suspend fun clear(scope: SubmissionScope, expectedId: String)
    suspend fun clearScope(scope: SubmissionScope)
}
