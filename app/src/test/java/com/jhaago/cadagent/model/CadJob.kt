package com.jhaago.cadagent.model

import java.time.Instant

data class CadJob(
    val id: String,
    val prompt: String,
    val state: JobState,
    val planValidated: Boolean,
    val hasUnresolvedAmbiguity: Boolean,
    val ambiguityMessage: String?,
    val isSimulated: Boolean,
    val outputPath: String?,
    val createdUtc: Instant,
    val updatedUtc: Instant,
    val revisions: List<JobRevision>,
) {
    val currentRevision: JobRevision?
        get() = revisions.maxByOrNull { it.sequence }
}
