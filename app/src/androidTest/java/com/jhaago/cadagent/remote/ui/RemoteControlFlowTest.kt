package com.jhaago.cadagent.remote.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.ui.CadAgentApp
import org.junit.Rule
import org.junit.Test

class RemoteControlFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unpairedWorkstationCannotStartRemoteOrCadTasks() {
        compose.setContent { CadAgentApp(AppContainer()) }

        compose.onNodeWithTag("remote-screen").assertIsDisplayed()
        compose.onNodeWithTag("unpaired-workstation").assertIsDisplayed()
        compose.onNodeWithTag("connect-remote").assertDoesNotExist()
        compose.onNodeWithTag("ai-instruction").assertDoesNotExist()
        compose.onNodeWithText("Connect demo").assertDoesNotExist()
        compose.onNodeWithText("Run demo task").assertDoesNotExist()
        compose.onNodeWithTag("advance-demo-task").assertDoesNotExist()
    }
}
