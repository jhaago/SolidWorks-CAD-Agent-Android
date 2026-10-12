package com.jhaago.cadagent.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.live.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appStartsWithSeparateCadChatRemoteAndSettingsTabs() {
        val container = AppContainer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        container.configureRemoteSettings(scope,
            object : RemoteTransport {
                override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse =
                    error("This navigation test must not contact a workstation")
            },
            object : RemoteCredentialStore {
                override fun read(endpoint: RemoteEndpoint): PairedWorkstation? = null
                override fun write(workstation: PairedWorkstation) = Unit
                override fun delete(endpoint: RemoteEndpoint) = Unit
            })
        compose.setContent { CadAgentApp(container) }

        compose.onNodeWithTag("cad-chat-screen").assertIsDisplayed()
        compose.onNodeWithTag("remote-screen").assertDoesNotExist()
        compose.onNodeWithText("Remote").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Jobs").assertDoesNotExist()
        compose.onNodeWithText("Simulation · no live PC connection").assertDoesNotExist()
        compose.onNodeWithText("Connect demo").assertDoesNotExist()
        compose.onNodeWithText("Remote").performClick()
        compose.onNodeWithTag("remote-screen").assertIsDisplayed()
        compose.onNodeWithTag("cad-chat-screen").assertDoesNotExist()
        compose.onNodeWithTag("connect-remote").assertDoesNotExist()
        compose.onNodeWithText("Pair a workstation in Settings before connecting.").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("pair-workstation").assertIsDisplayed()
        compose.onNodeWithTag("use-demo").assertDoesNotExist()
        compose.onNodeWithText("simulated examples").assertDoesNotExist()
        scope.cancel()
    }
}
