package com.jhaago.cadagent.model

import java.time.Instant

data class JobRevision(
    val id: String,
    val sequence: Int,
    val actions: List<String>,
    val createdUtc: Instant,
    val changeInstructions: String? = null,
)
