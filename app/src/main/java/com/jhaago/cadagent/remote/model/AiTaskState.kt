package com.jhaago.cadagent.remote.model

enum class AiTaskPhase { Idle, Running, Stopping, AwaitingClarification, AwaitingApproval, ReadyForReview, AwaitingProtectedAction, Completed, Stopped }
data class AiVerification(val checkName: String, val passed: Boolean, val expected: String?, val actual: String?)
data class AiArtifact(val fileName: String, val bytes: ByteArray)
data class AiTaskState(
    val id: String? = null,
    val instruction: String = "",
    val phase: AiTaskPhase = AiTaskPhase.Idle,
    val step: Int = 0,
    val message: String = "No AI task running",
    val revisionId: String? = null,
    val revisionNumber: Int? = null,
    val planVersion: Int? = null,
    val summary: String? = null,
    val assumptions: List<String> = emptyList(),
    val ambiguities: List<String> = emptyList(),
    val proposedCommands: List<String> = emptyList(),
    val verifications: List<AiVerification> = emptyList(),
    val outputPath: String? = null,
    val planValidated: Boolean = false,
    val actionPending: Boolean = false,
) {
    val active: Boolean get() = phase in setOf(AiTaskPhase.Running, AiTaskPhase.Stopping, AiTaskPhase.AwaitingProtectedAction,
        AiTaskPhase.AwaitingClarification, AiTaskPhase.AwaitingApproval, AiTaskPhase.ReadyForReview)
    val canApprove: Boolean get() = phase == AiTaskPhase.AwaitingApproval && revisionId != null && planValidated && ambiguities.isEmpty() && !actionPending && (planVersion != 2 || proposedCommands.isNotEmpty())
    val canRevise: Boolean get() = phase in setOf(AiTaskPhase.AwaitingClarification, AiTaskPhase.AwaitingApproval, AiTaskPhase.ReadyForReview) && revisionId != null && !actionPending && !(planVersion == 2 && planValidated)
    val canComplete: Boolean get() = phase == AiTaskPhase.ReadyForReview && !actionPending
}
