package com.jhaago.cadagent.remote.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.data.AiControlRepository
import com.jhaago.cadagent.remote.data.RemoteSessionRepository
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.display.RemoteDisplaySource
import com.jhaago.cadagent.remote.input.RemoteInputController
import com.jhaago.cadagent.remote.input.RemoteKeyboardEvent
import com.jhaago.cadagent.remote.input.RemotePointerEvent
import com.jhaago.cadagent.remote.model.RemoteConnectionState
import com.jhaago.cadagent.remote.model.RemoteControlMode
import com.jhaago.cadagent.remote.model.RemoteController
import com.jhaago.cadagent.remote.model.RemoteWorkstationStatus
import com.jhaago.cadagent.ui.theme.CadAgentTheme
import com.jhaago.cadagent.ui.CadAgentApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class FullScreenDesktopTest {
    @get:Rule val compose = createComposeRule()

    @Test fun desktopControlsRemainUnavailableUntilLiveWorkstationIsPaired() {
        compose.setContent { CadAgentApp(AppContainer()) }
        compose.onNodeWithText("Remote").performClick()
        compose.onNodeWithTag("remote-screen").assertIsDisplayed()
        compose.onNodeWithTag("unpaired-workstation").assertIsDisplayed()
        compose.onNodeWithTag("enter-full-screen").assertDoesNotExist()
        compose.onNodeWithTag("remote-display").assertDoesNotExist()
    }

    @Test fun connectedLiveFrameCanEnterAndExitFullScreenWithoutSendingInput() {
        val status = RemoteWorkstationStatus(connection = RemoteConnectionState.Connected,
            workstationName = "Test workstation", controller = RemoteController.User, isLive = true)
        val statusFlow = MutableStateFlow(status)
        val frame = RemoteDisplayFrame(800, 600, "Test desktop")
        val session = object : RemoteSessionRepository {
            override val status: StateFlow<RemoteWorkstationStatus> = statusFlow
            override fun connect(): Long? = null
            override fun disconnect() = Unit
            override fun setMode(mode: RemoteControlMode) = Unit
            override fun takeControl() = Unit
        }
        val ai = object : AiControlRepository {
            override val status: StateFlow<RemoteWorkstationStatus> = statusFlow
            override fun submitTask(instruction: String): String? = null
            override fun stopTask() = Unit
            override fun approveProtectedAction(id: String) = false
            override fun rejectProtectedAction(id: String) = false
        }
        val display = object : RemoteDisplaySource {
            override val frame: StateFlow<RemoteDisplayFrame?> = MutableStateFlow(frame)
        }
        var inputEvents = 0
        val input = object : RemoteInputController {
            override fun sendPointer(event: RemotePointerEvent): Boolean { inputEvents++; return true }
            override fun sendKeyboard(event: RemoteKeyboardEvent): Boolean { inputEvents++; return true }
            override fun releaseAll() = Unit
        }
        val actions = RemoteViewModel(session, ai, display, input)

        compose.setContent { CadAgentTheme { RemoteScreen(RemoteUiState(status, frame), actions) } }
        compose.onNodeWithTag("remote-content").performScrollToNode(hasTestTag("enter-full-screen"))
        compose.onNodeWithTag("enter-full-screen").performClick()
        compose.onNodeWithTag("full-screen-desktop").assertIsDisplayed()
        compose.onNodeWithTag("exit-full-screen").assertIsDisplayed()
        compose.onNodeWithTag("exit-full-screen").performClick()
        compose.onNodeWithTag("full-screen-desktop").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertEquals(0, inputEvents) }
    }
}
