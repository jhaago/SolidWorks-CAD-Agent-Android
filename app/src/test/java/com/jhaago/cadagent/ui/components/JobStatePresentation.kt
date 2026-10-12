package com.jhaago.cadagent.ui.components

import com.jhaago.cadagent.model.JobState

enum class JobStateTone {
    Neutral,
    Attention,
    Active,
    Success,
    Error,
}

data class JobStatePresentation(
    val label: String,
    val tone: JobStateTone,
)

fun jobStatePresentation(state: JobState): JobStatePresentation = when (state) {
    JobState.New -> JobStatePresentation("New", JobStateTone.Neutral)
    JobState.Interpreting -> JobStatePresentation("Interpreting", JobStateTone.Active)
    JobState.AwaitingClarification -> JobStatePresentation("Awaiting clarification", JobStateTone.Attention)
    JobState.AwaitingApproval -> JobStatePresentation("Awaiting approval", JobStateTone.Attention)
    JobState.Approved -> JobStatePresentation("Approved", JobStateTone.Active)
    JobState.Executing -> JobStatePresentation("Building", JobStateTone.Active)
    JobState.Verifying -> JobStatePresentation("Verifying", JobStateTone.Active)
    JobState.ReadyForReview -> JobStatePresentation("Ready for review", JobStateTone.Attention)
    JobState.Completed -> JobStatePresentation("Completed", JobStateTone.Success)
    JobState.Failed -> JobStatePresentation("Failed", JobStateTone.Error)
    JobState.Cancelled -> JobStatePresentation("Cancelled", JobStateTone.Neutral)
    is JobState.Unknown -> JobStatePresentation(
        label = state.rawValue.ifBlank { "Unknown" },
        tone = JobStateTone.Neutral,
    )
}
