package com.jhaago.cadagent.remote.ui.components

internal fun restoredRevisionDraft(saved: List<String>, jobId: String?, revisionId: String?): String =
    if (saved.size == 3 && saved[0] == jobId.orEmpty() && saved[1] == revisionId.orEmpty()) saved[2] else ""
