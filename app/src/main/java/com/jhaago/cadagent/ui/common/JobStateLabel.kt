package com.jhaago.cadagent.ui.common

import com.jhaago.cadagent.model.JobState

fun JobState.displayName(): String = when (this) {
    JobState.New -> "New"
    JobState.Interpreting -> "Interpreting"
    JobState.AwaitingClarification -> "Awaiting clarification"
    JobState.AwaitingApproval -> "Awaiting approval"
    JobState.Approved -> "Approved"
    JobState.Executing -> "Building"
    JobState.Verifying -> "Verifying"
    JobState.ReadyForReview -> "Ready for review"
    JobState.Completed -> "Completed"
    JobState.Failed -> "Failed"
    JobState.Cancelled -> "Cancelled"
    is JobState.Unknown -> rawValue.ifBlank { "Unknown" }
}
