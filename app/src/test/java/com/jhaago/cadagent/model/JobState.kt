package com.jhaago.cadagent.model

sealed interface JobState {
    data object New : JobState
    data object Interpreting : JobState
    data object AwaitingClarification : JobState
    data object AwaitingApproval : JobState
    data object Approved : JobState
    data object Executing : JobState
    data object Verifying : JobState
    data object ReadyForReview : JobState
    data object Completed : JobState
    data object Failed : JobState
    data object Cancelled : JobState
    data class Unknown(val rawValue: String) : JobState

    companion object {
        fun fromWireValue(raw: String): JobState = when (raw) {
            "New" -> New
            "Interpreting" -> Interpreting
            "AwaitingClarification" -> AwaitingClarification
            "AwaitingApproval" -> AwaitingApproval
            "Approved" -> Approved
            "Executing" -> Executing
            "Verifying" -> Verifying
            "ReadyForReview" -> ReadyForReview
            "Completed" -> Completed
            "Failed" -> Failed
            "Cancelled" -> Cancelled
            else -> Unknown(raw)
        }
    }
}
