package com.jhaago.cadagent.remote.model

enum class ProtectedActionKind { Start3dPrint }
enum class ProtectedActionDisposition { Pending, Approved, Rejected }
data class ProtectedActionRequest(
    val id: String,
    val taskId: String,
    val kind: ProtectedActionKind,
    val disposition: ProtectedActionDisposition = ProtectedActionDisposition.Pending,
) {
    init { require(id.isNotBlank() && taskId.isNotBlank()) }
}
