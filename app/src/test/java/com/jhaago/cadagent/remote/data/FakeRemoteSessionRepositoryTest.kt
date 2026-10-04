package com.jhaago.cadagent.remote.data

import com.jhaago.cadagent.remote.model.*
import org.junit.Assert.*
import org.junit.Test

class FakeRemoteSessionRepositoryTest {
    @Test fun `connect is idempotent and old connection callbacks cannot resurrect a disconnected session`() {
        val session = FakeRemoteSessionRepository()
        val first = session.connect()!!
        assertEquals(RemoteConnectionState.Connecting, session.status.value.connection)
        assertNull(session.connect())
        session.disconnect()
        assertFalse(session.finishConnecting(first))
        val second = session.connect()!!
        assertNotEquals(first, second)
        assertFalse(session.finishConnecting(first))
        assertTrue(session.finishConnecting(second))
        assertEquals(RemoteController.User, session.status.value.controller)
        assertNull(session.connect())
    }

    @Test fun `takeover atomically stops ai and leaves manual user authority`() {
        val session = connected()
        val ai = FakeAiControlRepository(session)
        session.setMode(RemoteControlMode.Agent)
        val task = ai.submitTask("Show model")!!
        assertEquals(RemoteController.Ai, session.status.value.controller)
        session.takeControl()
        val state = session.status.value
        assertEquals(RemoteControlMode.Manual, state.mode)
        assertEquals(RemoteController.User, state.controller)
        assertEquals(AiTaskPhase.Stopped, state.task.phase)
        assertFalse(ai.advanceTask(task))
        session.takeControl()
        assertEquals(state, session.status.value)
    }

    @Test fun `disconnect cancels task and pending action and reconnect never resumes ai`() {
        val session = connected()
        val ai = FakeAiControlRepository(session)
        session.setMode(RemoteControlMode.Agent)
        val task = ai.submitTask("Slice model")!!
        ai.advanceTask(task)
        ai.advanceTask(task)
        val action = session.status.value.protectedAction!!
        session.disconnect()
        assertEquals(RemoteController.None, session.status.value.controller)
        assertEquals(AiTaskPhase.Stopped, session.status.value.task.phase)
        assertEquals(ProtectedActionDisposition.Rejected, session.status.value.protectedAction!!.disposition)
        assertFalse(ai.approveProtectedAction(action.id))
        session.finishConnecting(session.connect()!!)
        assertEquals(RemoteControlMode.Manual, session.status.value.mode)
        assertEquals(RemoteController.User, session.status.value.controller)
        assertFalse(ai.advanceTask(task))
        assertEquals(AiTaskPhase.Stopped, session.status.value.task.phase)
    }

    @Test fun `mode changes stop existing task and disconnected mode changes are ignored`() {
        val session = connected()
        val ai = FakeAiControlRepository(session)
        session.setMode(RemoteControlMode.Assist)
        val task = ai.submitTask("Explain the desktop")!!
        assertEquals(RemoteController.User, session.status.value.controller)
        session.setMode(RemoteControlMode.Manual)
        assertEquals(AiTaskPhase.Stopped, session.status.value.task.phase)
        assertFalse(ai.advanceTask(task))
        session.disconnect()
        session.setMode(RemoteControlMode.Agent)
        session.takeControl()
        assertEquals(RemoteControlMode.Manual, session.status.value.mode)
        assertEquals(RemoteController.None, session.status.value.controller)
    }

    private fun connected() = FakeRemoteSessionRepository().also { it.finishConnecting(it.connect()!!) }
}
