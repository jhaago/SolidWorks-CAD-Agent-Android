package com.jhaago.cadagent.remote.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toPixelMap
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.live.*
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.ui.CadAgentApp
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveRemoteFlowTest {
    @get:Rule val compose = createComposeRule()
    private class Server : RemoteTransport {
        var epoch = 1L; var controlling = false; var frameId = 0L
        var approval = "pending"; var holdFrames = false
        val routes = java.util.concurrent.CopyOnWriteArrayList<String>()
        val jpeg = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.BLUE); it.compress(Bitmap.CompressFormat.JPEG, 70, out); it.recycle() }
        }.toByteArray()
        fun state() = """{"sessionId":"session","authorityEpoch":$epoch,"controlling":$controlling,"expiresAt":"2026-10-05T00:05:00Z"}"""
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            routes += operation.route
            val body = when (operation.route) {
                "pair/request" -> """{"requestId":"request","receiptSecret":"receipt"}"""
                "pair/status" -> """{"state":"$approval","deviceId":"device","credential":"secret"}"""
                "session/create" -> { controlling = false; """{"sessionToken":"token","session":${state()}}""" }
                "session/resume" -> { controlling = true; epoch++; state() }
                "session/release", "session/close" -> { controlling = false; epoch++; "{}" }
                "display/frame" -> { if (holdFrames) awaitCancellation(); """{"frameId":${++frameId},"displayGeneration":1,"width":64,"height":32,"capturedAt":"2026-10-05T00:00:00Z","cursorX":0.5,"cursorY":0.5,"jpegBytes":"${Base64.getEncoder().encodeToString(jpeg)}"}""" }
                else -> state()
            }
            return RemoteResponse(200, body)
        }
    }
    private class Store : RemoteCredentialStore {
        var record: PairedWorkstation? = null
        override fun read(endpoint: RemoteEndpoint) = record
        override fun write(workstation: PairedWorkstation) { record = workstation }
        override fun delete(endpoint: RemoteEndpoint) { record = null }
    }
    @Test fun liveFrameRequiresResumeAndNavigationReleasesWithoutResuming() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val server = Server(); val container = AppContainer()
        val driver = LiveConnectionDriver(scope, server, PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "secret"))
        container.selectLive(driver)
        try {
            compose.setContent { CadAgentApp(container) }
            compose.onNodeWithText("Remote").performClick()
            compose.onNodeWithTag("connect-remote").performClick()
            compose.waitUntil(5000) { driver.frame.value != null }
            compose.onNodeWithTag("controller").assertTextEquals("Control: No controller")
            compose.onNodeWithTag("remote-content").performScrollToNode(hasTestTag("remote-display"))
            compose.waitUntil(5000) { compose.onAllNodesWithTag("live-frame").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("live-frame").assertExists()
            val pixels = compose.onNodeWithTag("remote-display").captureToImage().toPixelMap()
            val blue = pixels[pixels.width / 2, pixels.height / 3]
            assertTrue("The real JPEG fixture must be rendered", blue.blue > .8f && blue.red < .2f)
            compose.onNodeWithTag("run-ai-task").assertDoesNotExist()
            compose.onNodeWithTag("take-control").performClick()
            compose.waitUntil(5000) { driver.status.value.controller == RemoteController.User }
            compose.onNodeWithText("Home").performClick()
            compose.waitUntil(5000) { driver.status.value.connection == RemoteConnectionState.Disconnected }
            compose.onNodeWithText("Remote").performClick()
            compose.waitUntil(5000) { driver.frame.value != null }
            compose.onNodeWithTag("controller").assertTextEquals("Control: No controller")
            compose.onNodeWithTag("take-control").performClick()
            compose.waitUntil(5000) { driver.status.value.controller == RemoteController.User }
            server.holdFrames = true
            compose.waitUntil(6000) { driver.frame.value == null && driver.status.value.controller == RemoteController.None }
            compose.onNodeWithTag("take-control").assertIsNotEnabled()
            compose.onNodeWithTag("stop-remote").performClick()
            compose.waitUntil(5000) { driver.status.value.connection == RemoteConnectionState.Disconnected }
        } finally { driver.close(); scope.cancel() }
    }
    @Test fun settingsShowsPendingAndLocalRejectionBeforeSuccessfulPairing() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val server = Server(); val store = Store(); val container = AppContainer()
        container.configureRemoteSettings(scope, server, store)
        try {
            compose.setContent { CadAgentApp(container) }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("workstation-endpoint").performTextInput("https://pc.example")
            compose.onNodeWithTag("pairing-secret").performTextInput("pair-secret")
            compose.onNodeWithTag("pair-workstation").performClick()
            compose.waitUntil(5000) { container.remoteSettings!!.state.value.pairing }
            compose.onNodeWithText("Accept this device in the Windows Remote Agent window.").assertExists()
            server.approval = "rejected"
            compose.waitUntil(5000) { container.remoteSettings!!.state.value.error != null }
            assertNull(store.record)
            server.approval = "approved"
            compose.onNodeWithTag("pairing-secret").performTextInput("new-secret")
            compose.onNodeWithTag("pair-workstation").performClick()
            compose.waitUntil(5000) { container.remoteSettings!!.state.value.paired }
            assertTrue(container.remoteAdapters.value.session.status.value.isLive)
        } finally { container.close(); scope.cancel() }
    }
}
