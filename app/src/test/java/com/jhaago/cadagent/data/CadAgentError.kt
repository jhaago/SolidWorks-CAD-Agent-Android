package com.jhaago.cadagent.data

import com.jhaago.cadagent.model.JobState

sealed class CadAgentError(message: String) : Exception(message) {
    class InvalidPrompt : CadAgentError("A CAD job prompt is required.")
    class InvalidInstructions : CadAgentError("Change instructions are required.")
    class NotFound(val jobId: String) : CadAgentError("CAD job '$jobId' was not found.")
    class StaleRevision(
        val expectedRevisionId: String?,
        val providedRevisionId: String,
    ) : CadAgentError("The displayed plan revision is stale. Reload the job before continuing.")

    class InvalidState(
        val jobId: String,
        val state: JobState,
    ) : CadAgentError("CAD job '$jobId' cannot perform that action in its current state.")
}
