package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.remote.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SubmissionLifecycleTest {
    private val paired = PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential")
    private val scope = SubmissionScope(paired.endpoint.origin, paired.deviceId)
    private val fixedId = "11111111-1111-4111-8111-111111111111"
    private val foreignId = "22222222-2222-4222-8222-222222222222"
    private fun TestScope.driver(server: RemoteTransport, store: PendingSubmissionStore) = LiveConnectionDriver(
        backgroundScope, server, paired, { testScheduler.currentTime }, { true }, store).also {
        it.setForeground(true); it.connect(); runCurrent(); it.setMode(RemoteControlMode.Agent)
    }

    @Test fun lostReceiptUsesExactIdDespiteForeignIdenticalPrompt() = runTest {
        val store = MemorySubmissions(); val server = SubmissionServer().also { it.loseReceipt = true }
        server.beforePost = { assertEquals(it, store.record?.id) }
        val driver = driver(server, store)
        assertNotNull(driver.submitAiTask("Build plate", CadPhoto(byteArrayOf(1, 2, 3)))); runCurrent()
        val id = store.record!!.id
        server.foreignActive = true
        advanceTimeBy(1100); runCurrent()
        assertEquals(id, driver.status.value.task.id)
        assertTrue(server.calls.any { it.route == "agent/jobs/$id" })
        assertFalse(server.calls.any { it.route == "agent/jobs/$foreignId" })
        assertEquals(1, server.calls.count { it.route == "agent/jobs" })
        driver.close()
    }

    @Test fun transient404RetriesOriginalPayloadOnceAndBlocksNewUuid() = runTest {
        val store = MemorySubmissions(); val server = SubmissionServer().also { it.loseReceipt = true; it.missing = true }
        val driver = driver(server, store)
        driver.submitAiTask("Build plate", CadPhoto(byteArrayOf(1, 2, 3))); runCurrent()
        repeat(4) { advanceTimeBy(1100); runCurrent() }
        val posts = server.calls.filter { it.route == "agent/jobs" }
        assertEquals(2, posts.size); assertEquals(posts[0].payload, posts[1].payload)
        assertNotNull(store.record); assertNull(driver.submitAiTask("New task"))
        driver.close()
    }

    @Test fun unexpectedReceiptOrConflictDoesNotClearOrSwitchIdentity() = runTest {
        for (conflict in listOf(false, true)) {
            val store = MemorySubmissions(); val server = SubmissionServer().also { it.wrongReceipt = !conflict; it.conflict = conflict; it.missing = true }
            val driver = driver(server, store)
            driver.submitAiTask("Build plate"); runCurrent()
            assertNotNull(store.record); assertNotEquals(foreignId, driver.status.value.task.id)
            assertNull(driver.submitAiTask("New task")); driver.close()
        }
    }

    @Test fun recreatedDriverFindsExactJobAndPersistedStopWithoutResending() = runTest {
        val store = MemorySubmissions().also { it.record = PendingSubmission(fixedId, true) }
        val server = SubmissionServer().also { it.jobId = fixedId; it.foreignActive = true }
        val driver = driver(server, store)
        advanceTimeBy(1100); runCurrent()
        assertTrue(server.calls.any { it.route == "agent/jobs/$fixedId/cancel" })
        assertFalse(server.calls.any { it.route == "agent/jobs" || it.route == "agent/jobs/$foreignId" })
        assertEquals(AiTaskPhase.Stopped, driver.status.value.task.phase); assertNull(store.record)
        driver.close()
    }

    @Test fun recreatedMissingJobCannotInventImageRetry() = runTest {
        val store = MemorySubmissions().also { it.record = PendingSubmission(fixedId, false) }
        val server = SubmissionServer().also { it.missing = true; it.foreignActive = true }
        val driver = driver(server, store)
        repeat(3) { advanceTimeBy(1100); runCurrent() }
        assertEquals(fixedId, store.record?.id)
        assertFalse(server.calls.any { it.route == "agent/jobs" || it.route == "agent/jobs/$foreignId" })
        assertNull(driver.submitAiTask("New task")); driver.close()
    }

    @Test fun storageFailureBeforePostPreventsSendingAndBlocksNewTask() = runTest {
        val store = MemorySubmissions().also { it.failWrite = true }
        val server = SubmissionServer(); val driver = driver(server, store)
        driver.submitAiTask("Build plate"); runCurrent()
        assertFalse(server.calls.any { it.route == "agent/jobs" })
        assertNull(driver.submitAiTask("New task")); assertTrue(driver.status.value.message!!.contains("tracking"))
        driver.close()
    }

    @Test fun stopDuringInitialPersistencePreventsPostAndPersistsIntent() = runTest {
        val store = MemorySubmissions().also { it.writeGate = CompletableDeferred() }
        val server = SubmissionServer(); val driver = driver(server, store)
        driver.submitAiTask("Build plate"); runCurrent(); driver.stopAiTask(); runCurrent()
        store.writeGate!!.complete(Unit); runCurrent()
        assertTrue(store.record!!.cancelRequested)
        assertFalse(server.calls.any { it.route == "agent/jobs" }); driver.close()
    }

    @Test fun reconnectAndRenewalRetainSubmissionAndTerminalConfirmationAllowsNewId() = runTest {
        val store = MemorySubmissions(); val server = SubmissionServer().also { it.loseReceipt = true }
        val driver = driver(server, store)
        driver.submitAiTask("Build plate"); runCurrent(); val first = store.record!!.id
        driver.disconnect(); driver.connect(); runCurrent(); advanceTimeBy(1100); runCurrent()
        assertEquals(first, driver.status.value.task.id)
        advanceTimeBy(240100); runCurrent(); assertEquals(first, store.record?.id)
        server.state = "Completed"; advanceTimeBy(1100); runCurrent(); assertNull(store.record)
        driver.setMode(RemoteControlMode.Agent); driver.submitAiTask("Build plate"); runCurrent()
        assertNotEquals(first, store.record?.id); driver.close()
    }

    @Test fun revokedDeviceClearsTrackingAndStopsReconnect() = runTest {
        val store = MemorySubmissions().also { it.record = PendingSubmission(fixedId, false) }
        val server = SubmissionServer().also { it.revoked = true }
        val driver = driver(server, store)
        assertNull(store.record); assertEquals(RemoteConnectionState.Disconnected, driver.status.value.connection)
        advanceTimeBy(4000); runCurrent()
        assertEquals(1, server.calls.count { it.route == "session/create" }); driver.close()
    }

    @Test fun cancelStorageFailurePreventsCancelAndMissingCapabilityKeepsLegacyProtocol() = runTest {
        val store = MemorySubmissions(); val server = SubmissionServer()
        val driver = driver(server, store)
        driver.submitAiTask("Build plate"); runCurrent(); store.failWrite = true
        driver.stopAiTask(); runCurrent()
        assertFalse(server.calls.any { it.route.endsWith("/cancel") }); assertNotNull(store.record); driver.close()
        val legacyStore = MemorySubmissions(); val legacy = SubmissionServer().also { it.supported = false }
        val old = driver(legacy, legacyStore)
        old.submitAiTask("Build plate"); runCurrent()
        assertNull(legacy.calls.single { it.route == "agent/jobs" }.payload["clientSubmissionId"])
        assertNull(legacyStore.record); old.close()
    }

    private class MemorySubmissions : PendingSubmissionStore {
        var record: PendingSubmission? = null
        var failWrite = false
        var writeGate: CompletableDeferred<Unit>? = null
        override suspend fun read(scope: SubmissionScope) = record
        override suspend fun write(scope: SubmissionScope, submission: PendingSubmission) {
            writeGate?.await(); if (failWrite) throw SubmissionStorageException()
            record = submission.copy(cancelRequested = submission.cancelRequested || record?.cancelRequested == true)
        }
        override suspend fun clear(scope: SubmissionScope, expectedId: String) { if (record?.id == expectedId) record = null }
        override suspend fun clearScope(scope: SubmissionScope) { record = null }
    }
    private inner class SubmissionServer : RemoteTransport {
        val calls = mutableListOf<RemoteOperation>()
        var jobId: String? = null
        var state = "AwaitingApproval"
        var loseReceipt = false; var missing = false; var wrongReceipt = false; var conflict = false; var foreignActive = false
        var revoked = false; var supported = true
        var beforePost: (String) -> Unit = {}
        fun job(id: String) = """{"id":"$id","prompt":"Build plate","state":"$state"}"""
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            calls += operation
            if (revoked && operation.route == "session/create") throw RemoteFailure(401, "device_unauthorized", "Pairing revoked")
            val body = when {
                operation.route == "session/create" || operation.route == "session/renew" -> """{"sessionToken":"session-token","session":{"sessionId":"session","authorityEpoch":1,"controlling":false,"expiresAt":"2026-10-06T00:05:00Z"}}"""
                operation.route == "session/heartbeat" -> """{"sessionId":"session","authorityEpoch":1,"controlling":false,"expiresAt":"2026-10-06T00:05:00Z"}"""
                operation.route == "agent/status" -> """{"agentHostAvailable":true,"jobInputImages":true,"clientSubmissionIds":$supported,"solidWorks":{"running":true,"attached":true,"visible":true},"activeJob":${if (foreignActive) job(foreignId) else "null"}}"""
                operation.route == "agent/jobs" -> {
                    val id = operation.payload["clientSubmissionId"] as? String ?: "33333333-3333-4333-8333-333333333333"
                    beforePost(id); jobId = id
                    if (conflict) throw RemoteFailure(409, "SUBMISSION_CONFLICT", "Different input")
                    if (loseReceipt) throw RemoteFailure(0, "timeout", "Receipt lost")
                    job(if (wrongReceipt) foreignId else id)
                }
                operation.route.endsWith("/cancel") -> { state = "Cancelled"; job(jobId ?: fixedId) }
                operation.route.startsWith("agent/jobs/") -> {
                    if (missing) throw RemoteFailure(404, "not_found", "No receipt yet")
                    job(jobId ?: fixedId)
                }
                operation.route == "display/frame" -> """{"frameId":1,"displayGeneration":1,"width":1,"height":1,"capturedAt":"2026-10-06T00:00:00Z","ageAtResponseMs":0,"cursorX":0.5,"cursorY":0.5,"jpegBytes":"/9j/2Q=="}"""
                else -> "{}"
            }
            return RemoteResponse(200, body)
        }
    }
}
