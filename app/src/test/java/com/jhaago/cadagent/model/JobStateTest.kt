package com.jhaago.cadagent.model

import org.junit.Assert.assertEquals
import org.junit.Test

class JobStateTest {
    @Test
    fun `all current Agent Host states map to known values`() {
        val states = listOf(
            "New" to JobState.New,
            "Interpreting" to JobState.Interpreting,
            "AwaitingClarification" to JobState.AwaitingClarification,
            "AwaitingApproval" to JobState.AwaitingApproval,
            "Approved" to JobState.Approved,
            "Executing" to JobState.Executing,
            "Verifying" to JobState.Verifying,
            "ReadyForReview" to JobState.ReadyForReview,
            "Completed" to JobState.Completed,
            "Failed" to JobState.Failed,
            "Cancelled" to JobState.Cancelled,
        )

        states.forEach { (wireValue, expected) ->
            assertEquals(expected, JobState.fromWireValue(wireValue))
        }
    }

    @Test
    fun `unknown future Agent Host state is preserved safely`() {
        assertEquals(
            JobState.Unknown("FutureServerState"),
            JobState.fromWireValue("FutureServerState"),
        )
    }
}
