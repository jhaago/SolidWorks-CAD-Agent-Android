package com.jhaago.cadagent.remote.data

import com.jhaago.cadagent.remote.model.*
import org.junit.Assert.*
import org.junit.Test

class FakeAiControlRepositoryTest {
    @Test fun `tasks require connection instruction and an ai enabled mode`() {
        val session = FakeRemoteSessionRepository()
        val ai = FakeAiControlRepository(session)
        assertNull(ai.submitTask("Inspect"))
        session.finishConnecting(session.connect()!!)
        assertNull(ai.submitTask("Inspect"))
        session.setMode(RemoteControlMode.Agent)
        assertNull(ai.submitTask("   "))
        assertNull(ai.submitTask("x".repeat(2001)))
        val id = ai.submitTask("Inspect")!!
        assertNull(ai.submitTask("Duplicate"))
        assertEquals(id, ai.status.value.task.id)
    }

    @Test fun `agent pauses for explicit print confirmation and decisions cannot be replayed`() {
        val session = connectedAgent()
        val ai = FakeAiControlRepository(session)
        val id = ai.submitTask("Prepare model")!!
        assertTrue(ai.advanceTask(id))
        assertTrue(ai.advanceTask(id))
        val action = ai.status.value.protectedAction!!
        assertEquals(AiTaskPhase.AwaitingProtectedAction, ai.status.value.task.phase)
        assertFalse(ai.advanceTask(id))
        assertFalse(ai.approveProtectedAction("wrong-id"))
        assertEquals(ProtectedActionDisposition.Pending, ai.status.value.protectedAction!!.disposition)
        assertTrue(ai.approveProtectedAction(action.id))
        assertEquals(AiTaskPhase.Completed, ai.status.value.task.phase)
        assertEquals(ProtectedActionDisposition.Approved, ai.status.value.protectedAction!!.disposition)
        assertFalse(ai.approveProtectedAction(action.id))
        assertFalse(ai.rejectProtectedAction(action.id))
        assertFalse(ai.advanceTask(id))
        assertEquals(RemoteController.None, session.status.value.controller)
    }

    @Test fun `reject stop and stale progress cannot complete a task or affect the next task`() {
        val session = connectedAgent()
        val ai = FakeAiControlRepository(session)
        val first = ai.submitTask("Prepare model")!!
        ai.advanceTask(first); ai.advanceTask(first)
        val action = ai.status.value.protectedAction!!.id
        assertTrue(ai.rejectProtectedAction(action))
        assertEquals(AiTaskPhase.Stopped, ai.status.value.task.phase)
        val second = ai.submitTask("Inspect model")!!
        assertFalse(ai.advanceTask(first))
        assertFalse(ai.approveProtectedAction(action))
        assertEquals(second, ai.status.value.task.id)
        ai.stopTask()
        val stopped = ai.status.value
        ai.stopTask()
        assertEquals(stopped, ai.status.value)
        assertFalse(ai.advanceTask(second))
    }

    @Test fun `assist observes while human remains controller and never asks to print`() {
        val session = connectedAgent()
        session.setMode(RemoteControlMode.Assist)
        val ai = FakeAiControlRepository(session)
        val id = ai.submitTask("What is visible?")!!
        assertEquals(RemoteController.User, ai.status.value.controller)
        ai.advanceTask(id)
        assertEquals(AiTaskPhase.Completed, ai.status.value.task.phase)
        assertNull(ai.status.value.protectedAction)
    }

    private fun connectedAgent() = FakeRemoteSessionRepository().also {
        it.finishConnecting(it.connect()!!)
        it.setMode(RemoteControlMode.Agent)
    }
}
