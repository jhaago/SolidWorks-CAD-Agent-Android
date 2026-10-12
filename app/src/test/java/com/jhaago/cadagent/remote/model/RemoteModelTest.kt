package com.jhaago.cadagent.remote.model

import org.junit.Assert.*
import org.junit.Test

class RemoteModelTest {
    @Test fun `default session has no input authority`() {
        val state = RemoteWorkstationStatus()
        assertEquals(RemoteConnectionState.Disconnected, state.connection)
        assertEquals(RemoteController.None, state.controller)
        assertEquals(AiTaskPhase.Idle, state.task.phase)
    }

    @Test fun `control modes and controllers represent the approved choices`() {
        assertEquals(listOf("Manual", "Assist", "Agent"), RemoteControlMode.entries.map { it.name })
        assertEquals(listOf("User", "Ai", "None"), RemoteController.entries.map { it.name })
    }

    @Test fun `disconnected or connecting session cannot claim input authority`() {
        for (connection in listOf(RemoteConnectionState.Disconnected, RemoteConnectionState.Connecting)) {
            for (controller in listOf(RemoteController.User, RemoteController.Ai)) {
                assertThrows(IllegalArgumentException::class.java) {
                    RemoteWorkstationStatus(connection = connection, controller = controller)
                }
            }
        }
    }

    @Test fun `protected requests have stable identity and explicit disposition`() {
        val pending = ProtectedActionRequest("print-1", "task-1", ProtectedActionKind.Start3dPrint)
        assertEquals(ProtectedActionDisposition.Pending, pending.disposition)
        val approved = pending.copy(disposition = ProtectedActionDisposition.Approved)
        assertEquals(pending.id, approved.id)
        assertEquals(pending.taskId, approved.taskId)
        assertNotEquals(approved.disposition, ProtectedActionDisposition.Rejected)
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedActionRequest("", "task-1", ProtectedActionKind.Start3dPrint)
        }
    }
}
