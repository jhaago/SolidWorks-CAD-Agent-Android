package com.jhaago.cadagent.remote.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.ui.CadAgentApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RemoteControlFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun startAgent(): AppContainer {
        val container = AppContainer()
        compose.setContent { CadAgentApp(container) }
        compose.onNodeWithText("Remote").performClick()
        compose.onNodeWithTag("connect-remote").performClick()
        compose.waitUntil(5000) { container.remoteSession.status.value.connection == RemoteConnectionState.Connected }
        compose.onNodeWithTag("mode-Agent").performScrollTo().performClick()
        compose.onNodeWithTag("ai-instruction").performScrollTo().performTextInput("Prepare the plate")
        compose.onNodeWithTag("run-ai-task").performScrollTo().performClick()
        return container
    }

    @Test fun takeoverStopsTaskWithoutResuming() {
        val container = startAgent()
        compose.onNodeWithTag("take-control").performClick()
        compose.onNodeWithTag("controller").assertTextContains("You")
        compose.runOnIdle {
            val state = container.remoteSession.status.value
            assertEquals(RemoteControlMode.Manual, state.mode)
            assertEquals(AiTaskPhase.Stopped, state.task.phase)
            assertFalse(container.aiControl.advanceTask(state.task.id!!))
        }
    }

    @Test fun printConfirmationCannotAutoApproveAndRejectStopsTask() {
        val container = startAgent()
        compose.onNodeWithTag("advance-demo-task").performScrollTo().performClick()
        compose.onNodeWithTag("advance-demo-task").performScrollTo().performClick()
        compose.onNodeWithTag("protected-action-dialog").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ProtectedActionDisposition.Pending, container.remoteSession.status.value.protectedAction!!.disposition)
        }
        compose.onNodeWithTag("reject-protected-action").performClick()
        compose.onNodeWithTag("protected-action-dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(AiTaskPhase.Stopped, container.remoteSession.status.value.task.phase) }
    }

    @Test fun takeoverIsAvailableWhileConfirmationIsOpen() {
        val container = startAgent()
        compose.onNodeWithTag("advance-demo-task").performScrollTo().performClick()
        compose.onNodeWithTag("advance-demo-task").performScrollTo().performClick()
        compose.onNodeWithTag("dialog-take-control").performClick()
        compose.onNodeWithTag("protected-action-dialog").assertDoesNotExist()
        compose.onNodeWithTag("controller").assertTextContains("You")
        compose.runOnIdle { assertEquals(AiTaskPhase.Stopped, container.remoteSession.status.value.task.phase) }
    }
}
