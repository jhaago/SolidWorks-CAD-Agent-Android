package com.jhaago.cadagent.remote.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.remote.model.*
import com.jhaago.cadagent.remote.ui.components.AiTaskPanel
import org.junit.Rule
import org.junit.Test

class PhoneJobPanelTest {
    @get:Rule val compose = createComposeRule()
    @Test fun liveApprovalIsExplicitAndDisplaysTheCurrentPlan() {
        val phase = AiTaskPhase.entries.single { it.name == "AwaitingApproval" }
        compose.setContent {
            AiTaskPanel(RemoteUiState(session = RemoteWorkstationStatus(isLive = true, task = AiTaskState(id = "job", phase = phase, revisionId = "revision", revisionNumber = 2, summary = "Plate plan", planValidated = true))), {}, {}, {})
        }
        compose.onNodeWithTag("approve-cad-plan").assertExists()
        compose.onNodeWithTag("revision-instructions").assertExists()
        compose.onNodeWithTag("cad-plan-summary").assertTextEquals("Plate plan")
        compose.onNodeWithText("Live tasks run through the paired Windows CAD Agent. Cancellation must be confirmed before manual input is re-enabled.").assertExists()
    }
}
