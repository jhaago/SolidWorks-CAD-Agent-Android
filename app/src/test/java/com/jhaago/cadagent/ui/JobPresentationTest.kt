package com.jhaago.cadagent.ui

import com.jhaago.cadagent.model.JobState
import com.jhaago.cadagent.ui.components.JobStateTone
import com.jhaago.cadagent.ui.components.jobStatePresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class JobPresentationTest {
    @Test
    fun `known job states have stable human readable labels`() {
        val expected = mapOf(
            JobState.New to "New",
            JobState.Interpreting to "Interpreting",
            JobState.AwaitingClarification to "Awaiting clarification",
            JobState.AwaitingApproval to "Awaiting approval",
            JobState.Approved to "Approved",
            JobState.Executing to "Building",
            JobState.Verifying to "Verifying",
            JobState.ReadyForReview to "Ready for review",
            JobState.Completed to "Completed",
            JobState.Failed to "Failed",
            JobState.Cancelled to "Cancelled",
        )

        expected.forEach { (state, label) ->
            assertEquals(label, jobStatePresentation(state).label)
        }
    }

    @Test
    fun `unknown future server state is preserved with neutral presentation`() {
        val presentation = jobStatePresentation(JobState.Unknown("FutureServerState"))

        assertEquals("FutureServerState", presentation.label)
        assertEquals(JobStateTone.Neutral, presentation.tone)
    }

    @Test
    fun `blank unknown server state falls back to Unknown`() {
        val presentation = jobStatePresentation(JobState.Unknown(""))

        assertEquals("Unknown", presentation.label)
        assertEquals(JobStateTone.Neutral, presentation.tone)
    }
}
