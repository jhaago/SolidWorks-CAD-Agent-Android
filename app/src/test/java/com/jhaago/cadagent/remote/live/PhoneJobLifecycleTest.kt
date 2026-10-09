package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.remote.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PhoneJobLifecycleTest {
    private val jobId = "11111111-1111-1111-1111-111111111111"
    private val revision = "22222222-2222-2222-2222-222222222222"
    private inner class Server : RemoteTransport {
        val calls = mutableListOf<RemoteOperation>()
        var state = "AwaitingApproval"
        var fixture: String? = null
        fun job() = fixture ?: """{"id":"$jobId","prompt":"Build plate","state":"$state","currentRevisionId":"$revision","currentRevisionNumber":2,"planValidated":true,"hasUnresolvedAmbiguity":false,"plan":{"Summary":"Plate plan","Assumptions":["Normal extrusion"],"Ambiguities":[],"ProposedCommands":["Save plate"]},"verifications":[{"revisionNumber":2,"checkName":"Rebuild","passed":true,"expected":{"hasErrors":false},"actual":{"hasErrors":false}}],"outputPath":"phone-tests/Plate.sldprt"}"""
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            calls += operation
            val session = """{"sessionId":"session","authorityEpoch":1,"controlling":false,"expiresAt":"2026-10-06T00:05:00Z"}"""
            return RemoteResponse(200, when {
                operation.route == "session/create" -> """{"sessionToken":"token","session":$session}"""
                operation.route == "agent/status" -> """{"agentHostAvailable":true,"executionMode":"Real","model":"test","solidWorks":{"running":true,"attached":true,"visible":true},"activeJob":${job()}}"""
                operation.route.endsWith("/artifact") -> """{"fileName":"Plate.sldprt","byteLength":4,"contentType":"application/octet-stream","base64":"UEFSVA=="}"""
                operation.route.startsWith("agent/jobs/") -> job()
                operation.route == "display/frame" -> """{"frameId":1,"displayGeneration":1,"width":1,"height":1,"capturedAt":"2026-10-06T00:00:00Z","ageAtResponseMs":0,"cursorX":0.5,"cursorY":0.5,"jpegBytes":"/9j/2Q=="}"""
                else -> session
            })
        }
    }
    @Test fun photoIsSubmittedWithNormalCadJobAndNoDesignBriefRoute() = runTest {
        val server = Server()
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "agent/status") return RemoteResponse(200,
                    """{"agentHostAvailable":true,"jobInputImages":true,"solidWorks":{"running":true,"attached":true,"visible":true},"activeJob":null}""")
                if (operation.route == "agent/jobs") {
                    server.calls += operation
                    return RemoteResponse(202, """{"id":"$jobId","prompt":"Model the photo","state":"Interpreting"}""")
                }
                return server.call(endpoint, operation)
            }
        }
        val driver = LiveConnectionDriver(backgroundScope, transport,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent(); driver.setMode(RemoteControlMode.Agent)
        assertNotNull(driver.submitAiTask("Model the photo", CadPhoto(byteArrayOf(1, 2, 3))))
        runCurrent()
        val request = server.calls.single { it.route == "agent/jobs" }
        assertEquals("Session", request.scheme)
        assertEquals("Model the photo", request.payload["prompt"])
        @Suppress("UNCHECKED_CAST")
        val image = request.payload["image"] as Map<String, String>
        assertEquals("image/jpeg", image["mediaType"])
        assertEquals("AQID", image["dataBase64"])
        assertFalse(server.calls.any { it.route.contains("designs") })
        driver.close()
    }
    @Test fun oldWindowsStatusCannotSilentlyDropPhoto() = runTest {
        val server = Server()
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "agent/status") return RemoteResponse(200,
                    """{"agentHostAvailable":true,"solidWorks":{"running":true,"attached":true,"visible":true},"activeJob":null}""")
                return server.call(endpoint, operation)
            }
        }
        val driver = LiveConnectionDriver(backgroundScope, transport,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent(); driver.setMode(RemoteControlMode.Agent)
        assertNull(driver.submitAiTask("Model photo", CadPhoto(byteArrayOf(1, 2, 3))))
        assertFalse(server.calls.any { it.route == "agent/jobs" })
        driver.close()
    }
    @Test fun revisionChangesAndCompletionUseSessionAuthenticatedRoutes() = runTest {
        val server = Server()
        val driver = LiveConnectionDriver(backgroundScope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"), { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        assertTrue(driver.requestChanges("Make the hole 25 mm")); runCurrent()
        val revisionCall = server.calls.single { it.route.endsWith("/request-changes") }
        assertEquals(revision, revisionCall.payload["revisionId"])
        assertEquals("Make the hole 25 mm", revisionCall.payload["instructions"])
        server.state = "ReadyForReview"
        advanceTimeBy(1100); runCurrent()
        val artifact = driver.downloadArtifact()!!
        assertEquals("Plate.sldprt", artifact.fileName)
        assertArrayEquals(byteArrayOf(80, 65, 82, 84), artifact.bytes)
        assertTrue(driver.completeTask()); runCurrent()
        val complete = server.calls.single { it.route.endsWith("/complete") }
        assertEquals("Session", complete.scheme)
        assertTrue(complete.payload.isEmpty())
        driver.close()
    }
    @Test fun lostSubmissionResponseKeepsControlBlockedAndRecoversJobForCancellation() = runTest {
        val server = Server().also { it.state = "Executing" }
        var reportActive = false
        val network = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "agent/jobs") throw RemoteFailure(0, "timeout", "Lost response")
                if (operation.route == "agent/status" && !reportActive) return RemoteResponse(200, """{"agentHostAvailable":true,"solidWorks":{"running":true,"attached":true,"visible":true},"activeJob":null}""")
                return server.call(endpoint, operation)
            }
        }
        val driver = LiveConnectionDriver(backgroundScope, network, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"), { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent(); driver.setMode(RemoteControlMode.Agent)
        assertNotNull(driver.submitAiTask("Build plate")); runCurrent()
        assertTrue(driver.status.value.task.active)
        driver.stopAiTask(); driver.resumeControl(); runCurrent()
        assertFalse(server.calls.any { it.route == "session/resume" })
        reportActive = true
        advanceTimeBy(1100); runCurrent()
        assertTrue(server.calls.any { it.route.endsWith("/cancel") })
        assertEquals(AiTaskPhase.Stopping, driver.status.value.task.phase)
        driver.close()
    }
    @Test fun actualNativeHostSnapshotSupportsPhoneReview() = runTest {
        val server = Server().also { it.fixture = javaClass.getResource("/native-ready-for-review.json")!!.readText() }
        val driver = LiveConnectionDriver(backgroundScope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"), { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        val task = driver.status.value.task
        assertEquals(AiTaskPhase.ReadyForReview, task.phase)
        assertEquals(2, task.revisionNumber)
        assertTrue(task.summary!!.contains("100 mm"))
        assertTrue(task.assumptions.isNotEmpty())
        assertTrue(task.proposedCommands.any { it.contains("SavePart") && it.contains("Plate-001.sldprt") })
        assertEquals(listOf("BodyCount", "BoundingBox", "RebuildErrors"), task.verifications.map { it.checkName })
        assertTrue(task.verifications.all { it.passed })
        assertTrue(task.outputPath!!.endsWith("Plate-001.sldprt"))
        assertTrue(task.canComplete)
        driver.close()
    }
    @Test fun versionTwoPlanShowsAllStepsBeforeApprovalAndDoesNotOfferUnsupportedRevision() = runTest {
        val server = Server().also { it.fixture = """{"id":"$jobId","prompt":"V2 rectangle boss: 20 x 10 x 5 mm; save as owned.sldprt","state":"AwaitingApproval","currentRevisionId":"$revision","currentRevisionNumber":1,"planValidated":true,"plan":{"planVersion":2,"summary":"Centred Top Plane rectangle and blind boss","assumptions":[],"ambiguities":[],"steps":[{"command":"NewPart","parameters":{}},{"command":"CreateSketch","parameters":{"plane":"Top Plane"}},{"command":"AddRectangle","parameters":{"centerXmm":0,"centerYmm":0,"widthMm":20,"heightMm":10}},{"command":"ExitSketch","parameters":{}},{"command":"Extrude","parameters":{"depthMm":5}},{"command":"SavePart","parameters":{"path":"owned.sldprt","allowOverwrite":false}}]},"verifications":[],"outputPath":null}""" }
        val driver = LiveConnectionDriver(backgroundScope, server,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        val task = driver.status.value.task
        assertEquals(2, task.planVersion)
        assertEquals(listOf("NewPart", "CreateSketch", "AddRectangle", "ExitSketch", "Extrude", "SavePart"),
            task.proposedCommands.map { it.substringBefore(' ') })
        assertTrue(task.proposedCommands[2].contains("widthMm"))
        assertTrue(task.proposedCommands[5].contains("owned.sldprt"))
        assertTrue(task.canApprove)
        assertFalse(task.canRevise)
        assertTrue(driver.approvePlan()); runCurrent()
        assertEquals(revision, server.calls.single { it.route.endsWith("/approve") }.payload["revisionId"])
        driver.close()
    }
    @Test fun versionTwoPlanWithoutDisplayableStepsCannotBeApprovedOnPhone() = runTest {
        val server = Server().also { it.fixture = """{"id":"$jobId","prompt":"V2 rectangle boss","state":"AwaitingApproval","currentRevisionId":"$revision","currentRevisionNumber":1,"planValidated":true,"plan":{"planVersion":2,"summary":"Incomplete plan","ambiguities":[]},"verifications":[]}""" }
        val driver = LiveConnectionDriver(backgroundScope, server,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        assertEquals(2, driver.status.value.task.planVersion)
        assertTrue(driver.status.value.task.proposedCommands.isEmpty())
        assertFalse(driver.status.value.task.canApprove)
        assertFalse(driver.approvePlan())
        assertFalse(server.calls.any { it.route.endsWith("/approve") })
        driver.close()
    }
    @Test fun cancellationResponseMustConfirmTerminalStateBeforeManualControl() = runTest {
        val server = Server().also { it.state = "Executing" }
        val driver = LiveConnectionDriver(backgroundScope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"), { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        driver.stopAiTask(); runCurrent()
        assertEquals("Stopping", driver.status.value.task.phase.name)
        driver.resumeControl(); runCurrent()
        assertFalse(server.calls.any { it.route == "session/resume" })
        server.state = "Cancelled"
        advanceTimeBy(1100); runCurrent()
        assertEquals("Stopped", driver.status.value.task.phase.name)
        driver.close()
    }
    @Test fun currentRevisionAndEvidenceArePresented() = runTest {
        val server = Server()
        val driver = LiveConnectionDriver(backgroundScope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"), { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        val task = driver.status.value.task
        assertEquals("AwaitingApproval", task.phase.name)
        assertEquals(revision, task.revisionId)
        assertEquals("Plate plan", task.summary)
        assertTrue(task.active)
        assertEquals("{\"hasErrors\":false}", task.verifications.single().actual)
        driver.close()
    }
    @Test fun briefStatusDoesNotEraseClarificationWhileDetailedRefreshIsDelayed() = runTest {
        val server = Server().also { it.state = "AwaitingClarification" }
        var failDetail = false
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route == "agent/status") return RemoteResponse(200,
                    """{"agentHostAvailable":true,"solidWorks":{"running":true,"attached":true,"visible":true},"activeJob":{"id":"$jobId","prompt":"Build plate","state":"AwaitingClarification"}}""")
                if (operation.route == "agent/jobs/$jobId" && failDetail)
                    throw RemoteFailure(0, "timeout", "Detailed job read timed out")
                return server.call(endpoint, operation)
            }
        }
        val driver = LiveConnectionDriver(backgroundScope, transport,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        advanceTimeBy(1100); runCurrent()
        assertEquals(revision, driver.status.value.task.revisionId)
        failDetail = true
        advanceTimeBy(1100); runCurrent()
        assertEquals(revision, driver.status.value.task.revisionId)
        assertTrue(driver.status.value.task.canRevise)
        driver.close()
    }
    @Test fun approvalSendsTheDisplayedRevisionOnce() = runTest {
        val server = Server()
        val driver = LiveConnectionDriver(backgroundScope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"), { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        val approve = driver.javaClass.methods.find { it.name == "approvePlan" }
        assertNotNull("Phone must approve displayed revision", approve)
        approve!!.invoke(driver); approve.invoke(driver); runCurrent()
        val calls = server.calls.filter { it.route.endsWith("/approve") }
        assertEquals(1, calls.size)
        assertEquals(revision, calls.single().payload["revisionId"])
        assertEquals("Session", calls.single().scheme)
        driver.close()
    }

    @Test fun staleApprovalCannotClearNewSessionActionAfterReconnect() = runTest {
        val server = Server()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        var approvals = 0
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
                if (operation.route.endsWith("/approve")) {
                    when (++approvals) { 1 -> first.await(); 2 -> second.await() }
                }
                return server.call(endpoint, operation)
            }
        }
        val driver = LiveConnectionDriver(backgroundScope, transport,
            PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"),
            { testScheduler.currentTime }, { true })
        driver.setForeground(true); driver.connect(); runCurrent()
        assertTrue(driver.approvePlan()); runCurrent()
        assertEquals(1, approvals)
        assertTrue(driver.status.value.task.actionPending)

        driver.disconnect(); runCurrent()
        driver.connect(); runCurrent()
        assertFalse(driver.status.value.task.actionPending)
        assertTrue(driver.approvePlan()); runCurrent()
        assertEquals(2, approvals)
        assertTrue(driver.status.value.task.actionPending)

        first.complete(Unit); runCurrent()
        assertTrue("Old approval must not clear the new action", driver.status.value.task.actionPending)
        second.complete(Unit); runCurrent()
        assertFalse(driver.status.value.task.actionPending)
        driver.close()
    }
}
