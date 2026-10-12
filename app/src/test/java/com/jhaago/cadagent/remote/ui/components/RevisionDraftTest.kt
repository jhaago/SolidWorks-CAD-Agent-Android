package com.jhaago.cadagent.remote.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RevisionDraftTest {
    @Test fun restoredDraftBelongsOnlyToItsSavedJobAndRevision() {
        val draft = listOf("job-a", "revision-a", "Increase thickness to 12 mm")
        assertEquals("Increase thickness to 12 mm", restoredRevisionDraft(draft, "job-a", "revision-a"))
        assertEquals("", restoredRevisionDraft(draft, "job-a", "revision-b"))
        assertEquals("", restoredRevisionDraft(draft, "job-b", "revision-a"))
        assertEquals("", restoredRevisionDraft(emptyList(), null, null))
    }
}
