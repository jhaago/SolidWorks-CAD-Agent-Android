package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiveConnectionDriverTest {
    private class Server : RemoteTransport {
        val operations = mutableListOf<RemoteOperation>()
        var epoch = 1L
        var controlling = false
        var generation = 1L
        var frameGate: CompletableDeferred<Unit>? = null
        var inputGate: CompletableDeferred<Unit>? = null
        var inputFail = false
        var heartbeatGate: CompletableDeferred<Unit>? = null
        var capturedAt = "2026-10-05T00:00:00Z"
        fun state() = """{"sessionId":"session","authorityEpoch":$epoch,"controlling":$controlling,"expiresAt":"2026-10-05T00:05:00Z"}"""
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            operations += operation
            val body = when (operation.route) {
                "session/create", "session/renew" -> { controlling = false; """{"sessionToken":"token","session":${state()}}""" }
                "session/resume", "session/take-control" -> { controlling = true; epoch++; state() }
                "session/release" -> { controlling = false; epoch++; "{}" }
                "session/close" -> { controlling = false; "{}" }
                "display/frame" -> {
                    frameGate?.await()
                    """{"frameId":1,"displayGeneration":$generation,"width":1,"height":1,"capturedAt":"$capturedAt","cursorX":0.5,"cursorY":0.5,"jpegBytes":"/9j/2Q=="}"""
                }
                "input" -> { inputGate?.await(); if (inputFail) throw RemoteFailure(0, "timeout", "Timed out"); "{}" }
                "session/heartbeat" -> { val snapshot = state(); heartbeatGate?.await(); snapshot }
                else -> state()
            }
            return RemoteResponse(200, body)
        }
    }
    private fun TestScope.driver(server: RemoteTransport) = LiveConnectionDriver(
        backgroundScope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
        nowMillis = { testScheduler.currentTime }, validateImage = { true },
    ).also { it.setForeground(true) }

    @Test fun reconnectBackoffIsOneTwoFourEightFifteen() = runTest {
        val attempts = mutableListOf<Long>()
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                attempts += testScheduler.currentTime
                throw RemoteFailure(0, "unavailable", "Unavailable")
            }
        }
        val driver = driver(transport); driver.connect(); runCurrent(); advanceTimeBy(30001); runCurrent()
        assertEquals(listOf(0L, 1000L, 3000L, 7000L, 15000L, 30000L), attempts)
        driver.disconnect(); advanceTimeBy(60000); runCurrent(); assertEquals(6, attempts.size)
    }
    @Test fun reconnectRequiresResumeAndClearsHeldInput() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        driver.resumeControl(); runCurrent(); assertEquals(RemoteController.User, driver.status.value.controller)
        assertTrue(driver.sendPointer(RemotePointerEvent(PointerAction.Down, .5f, .5f))); runCurrent()
        driver.disconnect(); runCurrent(); driver.connect(); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertEquals(1, server.operations.count { it.route == "input" })
    }
    @Test fun backgroundStopsRetryAndReleasesInput() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        driver.setForeground(false); runCurrent(); advanceTimeBy(60000); runCurrent()
        assertEquals(RemoteConnectionState.Disconnected, driver.status.value.connection)
        assertEquals(1, server.operations.count { it.route == "session/create" })
        assertTrue(server.operations.any { it.route == "session/close" })
    }
    @Test fun framePollingHasOneInflightAndFiveFpsLimit() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent(); advanceTimeBy(999); runCurrent()
        assertEquals(5, server.operations.count { it.route == "display/frame" })
        server.frameGate = CompletableDeferred(); advanceTimeBy(10000); runCurrent()
        assertEquals(6, server.operations.count { it.route == "display/frame" })
    }
    @Test fun staleFrameDisablesInput() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        server.frameGate = CompletableDeferred(); advanceTimeBy(4000); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertFalse(driver.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, .5f)))
    }
    @Test fun delayedPixelsCannotBecomeFreshByArrivingNow() = runTest {
        val server = Server().apply { capturedAt = "2026-10-04T23:59:50Z" }
        val driver = driver(server); driver.connect(); runCurrent()
        assertNull("A ten-second-old capture must not be displayed as fresh", driver.frame.value)
        driver.resumeControl(); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertFalse(driver.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, .5f)))
    }
    @Test fun delayedSuccessfulFrameResponseCannotEnableInput() = runTest {
        val server = Server().apply { frameGate = CompletableDeferred() }
        val driver = driver(server); driver.connect(); runCurrent()
        advanceTimeBy(4001); runCurrent()
        server.frameGate!!.complete(Unit); runCurrent()
        assertNull("A response delayed four seconds must not refresh old pixels", driver.frame.value)
        driver.resumeControl(); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
    }
    @Test fun wrongGenerationCannotEnqueue() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        server.generation = 2; advanceTimeBy(201); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertFalse(driver.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, .5f)))
    }
    @Test fun queueOverflowStopsControlAndReleaseCannotBeDropped() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        server.inputGate = CompletableDeferred()
        assertTrue(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Down))); runCurrent()
        repeat(100) { assertTrue(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Down))) }
        assertFalse(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Up))); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertTrue(server.operations.any { it.route == "session/release" })
        assertEquals(1, server.operations.count { it.route == "input" })
    }
    @Test fun inputTimeoutDoesNotRetry() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        server.inputFail = true; assertTrue(driver.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, .5f))); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, server.operations.count { it.route == "input" })
        assertEquals(RemoteController.None, driver.status.value.controller)
    }
    @Test fun disconnectCancelsLateCompletion() = runTest {
        val gate = CompletableDeferred<Unit>()
        val server = Server()
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "session/create") gate.await()
                return server.call(endpoint, operation)
            }
        }
        val driver = driver(transport); driver.connect(); runCurrent(); driver.disconnect(); gate.complete(Unit); runCurrent()
        assertEquals(RemoteConnectionState.Disconnected, driver.status.value.connection)
        assertEquals(RemoteController.None, driver.status.value.controller)
    }
    @Test fun lateHeartbeatCannotUndoFreshResume() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent()
        server.heartbeatGate = CompletableDeferred(); advanceTimeBy(1001); runCurrent()
        driver.resumeControl(); runCurrent()
        assertEquals(RemoteController.User, driver.status.value.controller)
        server.heartbeatGate!!.complete(Unit); runCurrent()
        assertEquals(RemoteController.User, driver.status.value.controller)
        assertTrue(driver.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, .5f))); runCurrent()
        assertEquals(2L, server.operations.last { it.route == "input" }.payload["authorityEpoch"])
    }
    @Test fun tokenRenewalIgnoresAFrameFailureForTheSupersededToken() = runTest {
        val server = Server(); val frameGate = CompletableDeferred<Unit>(); var token = "token"
        var blockFrame = false
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "session/renew") {
                    token = "renewed"
                    return RemoteResponse(200, """{"sessionToken":"renewed","session":${server.state()}}""")
                }
                if (operation.route == "display/frame" && blockFrame) {
                    blockFrame = false; frameGate.await()
                    if (operation.secret != token) throw RemoteFailure(401, "session_invalid", "Old token")
                }
                return server.call(endpoint, operation)
            }
        }
        val driver = driver(transport); driver.connect(); runCurrent(); advanceTimeBy(239600); runCurrent()
        blockFrame = true; advanceTimeBy(401); runCurrent()
        frameGate.complete(Unit); runCurrent()
        assertEquals(RemoteConnectionState.Connected, driver.status.value.connection)
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertEquals(1, server.operations.count { it.route == "session/create" })
    }
    @Test fun failedReleaseCannotKeepTheOldLeaseAlive() = runTest {
        val server = Server(); var opened = false
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "session/create") {
                    if (opened) throw RemoteFailure(409, "session_busy", "Busy")
                    opened = true
                }
                if (operation.route in setOf("session/release", "session/close"))
                    throw RemoteFailure(0, "connection_failed", "Release unavailable")
                return server.call(endpoint, operation)
            }
        }
        val driver = driver(transport); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        assertTrue(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Down))); runCurrent()
        assertEquals(1, server.operations.count { it.route == "input" })
        driver.releaseAll(); runCurrent(); advanceTimeBy(4001); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertEquals("An unconfirmed release must not extend the old input lease", 0, server.operations.count { it.route == "session/heartbeat" })
    }
    @Test fun pendingReleaseCannotKeepTheOldLeaseAlive() = runTest {
        val server = Server(); var opened = false; val release = CompletableDeferred<Unit>()
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "session/create") {
                    if (opened) throw RemoteFailure(409, "session_busy", "Busy")
                    opened = true
                }
                if (operation.route == "session/release") release.await()
                if (operation.route == "session/close") throw RemoteFailure(0, "connection_failed", "Close unavailable")
                return server.call(endpoint, operation)
            }
        }
        val driver = driver(transport); driver.connect(); runCurrent(); driver.resumeControl(); runCurrent()
        assertTrue(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Down))); runCurrent()
        driver.releaseAll(); runCurrent(); advanceTimeBy(2001); runCurrent()
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertEquals(0, server.operations.count { it.route == "session/heartbeat" })
    }

    @Test fun retryBackoffKeepsAnExplicitCancelActionAvailable() = runTest {
        var attempts = 0
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                attempts++; throw RemoteFailure(0, "unavailable", "Unavailable")
            }
        }
        val driver = driver(transport); driver.connect(); runCurrent()
        assertEquals("Retry waiting must keep Cancel connection visible", RemoteConnectionState.Connecting, driver.status.value.connection)
        driver.disconnect(); advanceTimeBy(5001); runCurrent()
        assertEquals(RemoteConnectionState.Disconnected, driver.status.value.connection)
        assertEquals(1, attempts)
    }

    @Test fun releasedInputStartsAFreshViewOnlySessionWithoutReplay() = runTest {
        val server = Server(); val driver = driver(server); driver.connect(); runCurrent()
        driver.resumeControl(); runCurrent()
        assertTrue(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Down))); runCurrent()
        driver.releaseAll(); runCurrent()
        assertEquals(2, server.operations.count { it.route == "session/create" })
        assertEquals(RemoteController.None, driver.status.value.controller)
        assertEquals(1, server.operations.count { it.route == "input" })
        assertFalse(driver.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Up)))
    }

    @Test fun staleHeartbeatCannotEraseAnImmediatelyReconnectedFrame() = runTest {
        val server = Server(); var creates = 0
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "session/create" && ++creates > 1) server.frameGate = null
                return server.call(endpoint, operation)
            }
        }
        val immediate = kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val driver = LiveConnectionDriver(immediate, transport,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            nowMillis = { testScheduler.currentTime }, validateImage = { true })
        driver.setForeground(true); driver.connect(); driver.resumeControl()
        server.frameGate = CompletableDeferred(); advanceTimeBy(3001); runCurrent()
        assertEquals(2, creates)
        assertNotNull("An old heartbeat callback must not clear the new session's frame", driver.frame.value)
        assertEquals(RemoteController.None, driver.status.value.controller)
    }

}
