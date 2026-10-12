package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.model.RemoteConnectionState
import com.jhaago.cadagent.remote.model.RemoteControlMode
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiveAiControlFlowTest {
    private class Server : RemoteTransport {
        val operations = mutableListOf<RemoteOperation>()
        private var epoch = 1L
        private val jobId = "11111111-1111-1111-1111-111111111111"

        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            operations += operation
            val body = when {
                operation.route == "session/create" ->
                    """{"sessionToken":"token","session":${session(false)}}"""
                operation.route == "session/heartbeat" -> session(false)
                operation.route == "display/frame" ->
                    """{"frameId":1,"displayGeneration":1,"width":1,"height":1,"capturedAt":"2026-10-06T00:00:00Z","ageAtResponseMs":0,"cursorX":0.5,"cursorY":0.5,"jpegBytes":"/9j/2Q=="}"""
                operation.route == "agent/status" ->
                    """{"agentHostAvailable":true,"executionMode":"Real","model":"gpt-5.6-sol","solidWorks":{"running":true,"attached":true,"visible":true,"version":"SOLIDWORKS 2020 SP5.0","activeDocument":"Bracket.SLDPRT"},"activeJob":null}"""
                operation.route == "agent/jobs" ->
                    """{"id":"$jobId","prompt":"Make the bracket 10 mm thicker","state":"Executing","planValidated":true,"hasUnresolvedAmbiguity":false,"ambiguityMessage":null,"currentRevisionId":null,"currentRevisionNumber":null,"plan":null,"commandCount":1,"verifications":[]}"""
                operation.route == "agent/jobs/$jobId" ->
                    """{"id":"$jobId","prompt":"Make the bracket 10 mm thicker","state":"Executing","planValidated":true,"hasUnresolvedAmbiguity":false,"ambiguityMessage":null,"currentRevisionId":null,"currentRevisionNumber":null,"plan":null,"commandCount":1,"verifications":[]}"""
                operation.route == "agent/jobs/$jobId/cancel" ->
                    """{"id":"$jobId","prompt":"Make the bracket 10 mm thicker","state":"Cancelled","planValidated":true,"hasUnresolvedAmbiguity":false,"ambiguityMessage":null,"currentRevisionId":null,"currentRevisionNumber":null,"plan":null,"commandCount":1,"verifications":[]}"""
                operation.route == "session/close" || operation.route == "session/release" -> "{}"
                else -> session(false)
            }
            return RemoteResponse(200, body)
        }

        private fun session(controlling: Boolean): String =
            """{"sessionId":"session","authorityEpoch":${epoch++},"controlling":$controlling,"expiresAt":"2026-10-06T00:05:00Z"}"""
    }

    private fun kotlinx.coroutines.test.TestScope.liveContainer(server: Server): AppContainer {
        val driver = LiveConnectionDriver(
            backgroundScope,
            server,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            nowMillis = { testScheduler.currentTime },
            validateImage = { true },
        )
        driver.setForeground(true)
        return AppContainer().also {
            assertTrue(it.selectLive(driver))
            it.remoteAdapters.value.session.connect()
        }
    }

    @Test
    fun liveAdaptersAllowAssistModeAndSubmitRemoteTask() = runTest {
        val server = Server()
        val container = liveContainer(server)
        runCurrent()
        val adapters = container.remoteAdapters.value
        assertEquals(RemoteConnectionState.Connected, adapters.session.status.value.connection)

        adapters.session.setMode(RemoteControlMode.Assist)
        assertEquals(RemoteControlMode.Assist, adapters.session.status.value.mode)

        val requestId = adapters.ai.submitTask("Make the bracket 10 mm thicker")
        assertNotNull(requestId)
        runCurrent()
        assertTrue(server.operations.any { it.route == "agent/jobs" })
        assertTrue(adapters.ai.status.value.task.active)
        assertEquals("11111111-1111-1111-1111-111111111111", adapters.ai.status.value.task.id)
    }

    @Test
    fun liveStopCancelsTheActiveAgentJobWithoutDisconnecting() = runTest {
        val server = Server()
        val container = liveContainer(server)
        runCurrent()
        val adapters = container.remoteAdapters.value
        adapters.session.setMode(RemoteControlMode.Assist)
        assertNotNull(adapters.ai.submitTask("Make the bracket 10 mm thicker"))
        runCurrent()

        adapters.ai.stopTask()
        runCurrent()

        assertTrue(server.operations.any { it.route == "agent/jobs/11111111-1111-1111-1111-111111111111/cancel" })
        assertEquals(RemoteConnectionState.Connected, adapters.session.status.value.connection)
        assertEquals("Stopped", adapters.ai.status.value.task.phase.name)
    }

    @Test
    fun liveStatusCarriesAgentAndSolidWorksRuntimeDetails() = runTest {
        val server = Server()
        val container = liveContainer(server)
        runCurrent()
        val status = container.remoteAdapters.value.session.status.value
        val methods = status.javaClass.methods.associateBy { it.name }

        fun value(getter: String): Any? {
            val method = methods[getter]
            assertNotNull("RemoteWorkstationStatus must expose $getter", method)
            return method!!.invoke(status)
        }

        assertEquals("Real", value("getExecutionMode"))
        assertEquals("gpt-5.6-sol", value("getAiModel"))
        assertEquals(true, value("getSolidWorksRunning"))
        assertEquals(true, value("getSolidWorksAttached"))
        assertEquals("SOLIDWORKS 2020 SP5.0", value("getSolidWorksVersion"))
        assertEquals("Bracket.SLDPRT", value("getActiveDocument"))
    }
}
