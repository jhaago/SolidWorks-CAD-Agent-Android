package com.jhaago.cadagent.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.di.AppContainer
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteDoesNotResetCadJobs() {
        val container = AppContainer()
        val jobs = container.repository.jobs.value
        compose.setContent { CadAgentApp(container) }
        compose.onNodeWithText("Remote").performClick()
        compose.onNodeWithTag("remote-screen").assertIsDisplayed()
        compose.onNodeWithText("Jobs").performClick()
        compose.onNodeWithText("All jobs").assertIsDisplayed()
        compose.onNodeWithText(jobs.first().prompt).assertExists()
        compose.onNodeWithText("Home").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(jobs, container.repository.jobs.value) }
    }
}
