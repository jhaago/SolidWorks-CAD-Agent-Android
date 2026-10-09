package com.jhaago.cadagent.model

enum class AgentAvailability {
    Available,
    Unavailable,
    NotConfigured,
    Unknown,
}

data class AgentStatus(
    val availability: AgentAvailability,
    val solidWorksConnected: Boolean? = null,
    val solidWorksVersion: String? = null,
)
