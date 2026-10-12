package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.remote.model.CadPhoto

internal class SubmissionRecovery(
    var pending: PendingSubmission,
    val instruction: String? = null,
    val photo: CadPhoto? = null,
    var retryCount: Int = 0,
) {
    fun canRetry(authorizedForeground: Boolean): Boolean = authorizedForeground && instruction != null &&
        !pending.cancelRequested && retryCount < 1
}
