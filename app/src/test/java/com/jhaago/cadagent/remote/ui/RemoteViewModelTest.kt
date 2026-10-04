package com.jhaago.cadagent.remote.ui

import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.FakeRemoteDisplaySource
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test fun `connect presents connecting then connected and cancellation prevents late recovery`() = runTest {
        val session = FakeRemoteSessionRepository()
        val vm = viewModel(session)
        assertEquals(RemoteConnectionState.Disconnected, vm.uiState.value.session.connection)
        vm.connect(); vm.connect()
        assertEquals(RemoteConnectionState.Connecting, vm.uiState.value.session.connection)
        advanceTimeBy(500); runCurrent()
        assertEquals(RemoteConnectionState.Connected, vm.uiState.value.session.connection)
        vm.disconnect()
        vm.connect()
        vm.disconnect()
        advanceUntilIdle()
        assertEquals(RemoteConnectionState.Disconnected, vm.uiState.value.session.connection)
    }

    @Test fun `takeover rejects late task progress and enables manual input immediately`() {
        val session = connected()
        val input = FakeRemoteInputController(session)
        val vm = viewModel(session, input)
        vm.selectMode(RemoteControlMode.Agent)
        vm.changeInstruction("Show model")
        vm.runTask(); vm.runTask()
        val id = vm.uiState.value.session.task.id!!
        vm.takeControl()
        assertEquals(RemoteControlMode.Manual, vm.uiState.value.session.mode)
        assertFalse(FakeAiControlRepository(session).advanceTask(id))
        assertTrue(vm.pointer(RemotePointerEvent(PointerAction.Click, .5f, .5f)))
        assertEquals(1, input.events.value.size)
    }

    @Test fun `manual gesture interrupts agent rather than fighting for input`() {
        val session = connected()
        val vm = viewModel(session)
        vm.selectMode(RemoteControlMode.Agent)
        vm.changeInstruction("Prepare model"); vm.runTask()
        assertTrue(vm.pointer(RemotePointerEvent(PointerAction.Down, .2f, .4f)))
        assertEquals(AiTaskPhase.Stopped, vm.uiState.value.session.task.phase)
        assertEquals(RemoteController.User, vm.uiState.value.session.controller)
    }

    @Test fun `pending action requires explicit decision and disconnect makes stale approval harmless`() {
        val session = connected()
        val vm = viewModel(session)
        vm.selectMode(RemoteControlMode.Agent)
        vm.changeInstruction("Prepare model"); vm.runTask()
        vm.advanceDemoTask(); vm.advanceDemoTask()
        val request = vm.uiState.value.session.protectedAction!!
        assertEquals(ProtectedActionDisposition.Pending, request.disposition)
        vm.disconnect()
        vm.approve(request.id)
        assertEquals(AiTaskPhase.Stopped, vm.uiState.value.session.task.phase)
        assertEquals(RemoteController.None, vm.uiState.value.session.controller)
    }

    @Test fun `assist task shows suggestions while leaving manual authority`() {
        val vm = viewModel(connected())
        vm.selectMode(RemoteControlMode.Assist)
        vm.changeInstruction("Explain desktop"); vm.runTask()
        assertEquals(RemoteController.User, vm.uiState.value.session.controller)
        vm.advanceDemoTask()
        assertEquals(AiTaskPhase.Completed, vm.uiState.value.session.task.phase)
        assertNull(vm.uiState.value.session.protectedAction)
    }

    @Test fun `invalid instruction gives recoverable feedback without starting a task`() {
        val vm = viewModel(connected())
        vm.selectMode(RemoteControlMode.Agent)
        vm.runTask()
        assertNotNull(vm.uiState.value.error)
        assertEquals(AiTaskPhase.Idle, vm.uiState.value.session.task.phase)
        vm.changeInstruction("Inspect model")
        assertNull(vm.uiState.value.error)
        vm.runTask()
        assertEquals(AiTaskPhase.Running, vm.uiState.value.session.task.phase)
    }

    private fun connected() = FakeRemoteSessionRepository().also { it.finishConnecting(it.connect()!!) }
    private fun viewModel(session: FakeRemoteSessionRepository, input: FakeRemoteInputController = FakeRemoteInputController(session)) =
        RemoteViewModel(session, FakeAiControlRepository(session), FakeRemoteDisplaySource(), input, FakeRemoteDemoDriver(session, FakeAiControlRepository(session)))
}
