package com.jhaago.cadagent.remote.ui

import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.FakeRemoteDisplaySource
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.display.RemoteDisplaySource
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        assertTrue(session.finishConnecting(1L))
        assertEquals(RemoteConnectionState.Connected, vm.uiState.value.session.connection)
        vm.disconnect()
        vm.connect()
        vm.disconnect()
        assertFalse(session.finishConnecting(2L))
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
        val ai = FakeAiControlRepository(session)
        ai.advanceTask(vm.uiState.value.session.task.id!!); ai.advanceTask(vm.uiState.value.session.task.id!!)
        val request = vm.uiState.value.session.protectedAction!!
        assertEquals(ProtectedActionDisposition.Pending, request.disposition)
        vm.disconnect()
        vm.approve(request.id)
        assertEquals(AiTaskPhase.Stopped, vm.uiState.value.session.task.phase)
        assertEquals(RemoteController.None, vm.uiState.value.session.controller)
    }

    @Test fun `assist task shows suggestions while leaving manual authority`() {
        val session = connected()
        val ai = FakeAiControlRepository(session)
        val vm = viewModel(session)
        vm.selectMode(RemoteControlMode.Assist)
        vm.changeInstruction("Explain desktop"); vm.runTask()
        assertEquals(RemoteController.User, vm.uiState.value.session.controller)
        ai.advanceTask(vm.uiState.value.session.task.id!!)
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
    @Test fun `viewmodel disposal disconnects and releases held input`() {
        val session = connected(); val input = FakeRemoteInputController(session); val vm = viewModel(session, input)
        vm.pointer(RemotePointerEvent(PointerAction.Down, .5f, .5f))
        assertTrue(input.hasHeldInput)
        val store = androidx.lifecycle.ViewModelStore(); store.put("remote", vm); store.clear()
        assertFalse(input.hasHeldInput)
        assertEquals(RemoteConnectionState.Disconnected, session.status.value.connection)
    }
    @Test fun `cad task starts from the default mode without a mode choice`() = runTest {
        val session = connected().also { it.enterViewOnly() }
        val vm = viewModel(session)
        vm.changeInstruction("Create a plate")
        runCurrent()
        vm.runTask()
        assertEquals(RemoteControlMode.Agent, session.status.value.mode)
        assertEquals(AiTaskPhase.Running, session.status.value.task.phase)
    }

    @Test fun `photo is retained until a task is submitted and can be removed`() = runTest {
        val session = connected().also { it.enterViewOnly() }
        val vm = viewModel(session)
        vm.attachPhoto(byteArrayOf(1, 2, 3))
        runCurrent()
        assertNotNull(vm.uiState.value.photo)
        vm.removePhoto(); runCurrent()
        assertNull(vm.uiState.value.photo)
        vm.attachPhoto(byteArrayOf(1, 2, 3))
        vm.changeInstruction("Model the sketch")
        runCurrent()
        vm.runTask(); runCurrent()
        assertEquals(AiTaskPhase.Running, session.status.value.task.phase)
        assertNull(vm.uiState.value.photo)
    }
    @Test fun `photo cannot be submitted to a workstation without image support`() = runTest {
        val session = connected().also { it.enterViewOnly(supportsJobImages = false) }
        val vm = viewModel(session)
        vm.attachPhoto(byteArrayOf(1, 2, 3))
        vm.changeInstruction("Model the sketch")
        runCurrent()
        assertFalse(vm.uiState.value.canRunTask)
        vm.runTask(); runCurrent()
        assertEquals(AiTaskPhase.Idle, session.status.value.task.phase)
        assertTrue(vm.uiState.value.error!!.contains("Update the Windows CAD Agent"))
    }

    @Test fun `viewmodel disposal leaves durable workstation job running`() {
        val active = RemoteWorkstationStatus(connection = RemoteConnectionState.Connected, isLive = true,
            task = AiTaskState(id = "job-1", instruction = "Build bracket", phase = AiTaskPhase.Running))
        val statusFlow = MutableStateFlow(active)
        var disconnects = 0
        var stops = 0
        val session = object : RemoteSessionRepository {
            override val status: StateFlow<RemoteWorkstationStatus> = statusFlow
            override fun connect(): Long? = null
            override fun disconnect() { disconnects++ }
            override fun setMode(mode: RemoteControlMode) = Unit
            override fun takeControl() = Unit
        }
        val ai = object : AiControlRepository {
            override val status: StateFlow<RemoteWorkstationStatus> = statusFlow
            override fun submitTask(instruction: String): String? = null
            override fun stopTask() { stops++ }
            override fun approveProtectedAction(id: String) = false
            override fun rejectProtectedAction(id: String) = false
        }
        val display = object : RemoteDisplaySource {
            override val frame: StateFlow<RemoteDisplayFrame?> = MutableStateFlow(null)
        }
        val input = object : RemoteInputController {
            override fun sendPointer(event: RemotePointerEvent) = false
            override fun sendKeyboard(event: RemoteKeyboardEvent) = false
            override fun releaseAll() = Unit
        }
        val vm = RemoteViewModel(session, ai, display, input)
        val store = androidx.lifecycle.ViewModelStore(); store.put("remote", vm); store.clear()

        assertEquals(1, disconnects)
        assertEquals(0, stops)
        assertTrue(statusFlow.value.task.active)
    }

    private fun connected() = FakeRemoteSessionRepository().also { it.finishConnecting(it.connect()!!) }
    private fun viewModel(session: FakeRemoteSessionRepository, input: FakeRemoteInputController = FakeRemoteInputController(session)) =
        RemoteViewModel(session, FakeAiControlRepository(session), FakeRemoteDisplaySource(), input)
}
