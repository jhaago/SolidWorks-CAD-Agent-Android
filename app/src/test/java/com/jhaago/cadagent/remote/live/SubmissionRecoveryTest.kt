package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.remote.model.CadPhoto
import org.junit.Assert.*
import org.junit.Test

class SubmissionRecoveryTest {
    private val pending = PendingSubmission("11111111-1111-4111-8111-111111111111", false)
    @Test fun originalInputAllowsOneForegroundRetryOnly() {
        val state = SubmissionRecovery(pending, "plate", CadPhoto(byteArrayOf(1, 2)))
        assertFalse(state.canRetry(false)); assertTrue(state.canRetry(true))
        state.retryCount++
        assertFalse(state.canRetry(true))
    }
    @Test fun recreatedOrCancelledSubmissionCannotResendMissingInput() {
        assertFalse(SubmissionRecovery(pending).canRetry(true))
        assertFalse(SubmissionRecovery(pending.copy(cancelRequested = true), "plate").canRetry(true))
    }
}
