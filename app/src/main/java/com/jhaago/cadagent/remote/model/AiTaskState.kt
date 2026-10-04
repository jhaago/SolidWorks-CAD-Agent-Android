package com.jhaago.cadagent.remote.model

enum class AiTaskPhase { Idle, Running, AwaitingProtectedAction, Completed, Stopped }
data class AiTaskState(
    val id: String? = null,
    val instruction: String = "",
    val phase: AiTaskPhase = AiTaskPhase.Idle,
    val step: Int = 0,
    val message: String = "No AI task running",
) {
    val active: Boolean get() = phase == AiTaskPhase.Running || phase == AiTaskPhase.AwaitingProtectedAction
}
