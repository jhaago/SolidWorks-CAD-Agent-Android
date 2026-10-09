package com.jhaago.cadagent.remote.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.jhaago.cadagent.MainActivity
import com.jhaago.cadagent.remote.live.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.ui.CadAgentAppViewModel
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveRemoteLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private class Server : RemoteTransport {
        var epoch = 1L; var controlling = false; var frame = 0
        var connecting: CompletableDeferred<Unit>? = null
        val routes = java.util.concurrent.CopyOnWriteArrayList<String>()
        private val jpeg = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { it.compress(Bitmap.CompressFormat.JPEG, 70, out); it.recycle() }
        }.toByteArray()
        private fun state() = """{"sessionId":"session","authorityEpoch":$epoch,"controlling":$controlling,"expiresAt":"2026-10-05T00:05:00Z"}"""
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            routes += operation.route
            return RemoteResponse(200, when (operation.route) {
                "session/create" -> { connecting?.await(); controlling = false; """{"sessionToken":"token","session":${state()}}""" }
                "session/resume" -> { controlling = true; epoch++; state() }
                "session/close", "session/release" -> { controlling = false; epoch++; "{}" }
                "agent/status" -> """{"agentHostAvailable":true,"executionMode":"Real","model":"gpt-5.6-sol","solidWorks":{"running":true,"attached":true,"visible":true,"version":"SOLIDWORKS 2020","activeDocument":null},"activeJob":null}"""
                "display/frame" -> """{"frameId":${++frame},"displayGeneration":1,"width":1,"height":1,"capturedAt":"2026-10-05T00:00:00Z","ageAtResponseMs":0,"cursorX":0.5,"cursorY":0.5,"jpegBytes":"${Base64.getEncoder().encodeToString(jpeg)}"}"""
                else -> state()
            })
        }
    }
    @Test fun rotationAndBackgroundReleaseControlAndKeepTheSameRepository() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate); val server = Server()
        val driver = LiveConnectionDriver(scope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"))
        lateinit var original: com.jhaago.cadagent.di.AppContainer
        compose.runOnIdle { original = ViewModelProvider(compose.activity)[CadAgentAppViewModel::class.java].container; assertTrue(original.selectLive(driver)) }
        try {
            compose.onNodeWithText("Remote").performClick(); compose.onNodeWithTag("connect-remote").performClick()
            compose.waitUntil(5000) { driver.frame.value != null }
            compose.onNodeWithTag("take-control").performClick()
            compose.waitUntil(5000) { driver.status.value.controller == RemoteController.User }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(5000) { driver.frame.value != null && driver.status.value.controller == RemoteController.None }
            compose.runOnIdle { assertSame(original, ViewModelProvider(compose.activity)[CadAgentAppViewModel::class.java].container) }
            assertTrue(server.routes.any { it == "session/close" })
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitUntil(5000) { driver.status.value.connection == RemoteConnectionState.Disconnected }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(5000) { driver.frame.value != null }
            assertEquals(RemoteController.None, driver.status.value.controller)
        } finally { driver.close(); scope.cancel() }
    }
    @Test fun backgroundDuringConnectCancelsLateCompletion() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val server = Server().apply { connecting = CompletableDeferred() }
        val driver = LiveConnectionDriver(scope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"))
        compose.runOnIdle { ViewModelProvider(compose.activity)[CadAgentAppViewModel::class.java].container.selectLive(driver) }
        try {
            compose.onNodeWithText("Remote").performClick(); compose.onNodeWithTag("connect-remote").performClick()
            compose.waitUntil(5000) { server.routes.contains("session/create") }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            server.connecting!!.complete(Unit)
            compose.waitUntil(5000) { driver.status.value.connection == RemoteConnectionState.Disconnected }
            assertEquals(RemoteController.None, driver.status.value.controller)
            assertNull(driver.frame.value)
        } finally { driver.close(); scope.cancel() }
    }
}
