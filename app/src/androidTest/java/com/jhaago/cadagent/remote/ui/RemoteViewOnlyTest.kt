package com.jhaago.cadagent.remote.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.ui.components.RemoteDisplaySurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemoteViewOnlyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun viewModeTouchDoesNotSendRemoteInput() {
        var events = 0
        compose.setContent { RemoteDisplaySurface(RemoteDisplayFrame(1920, 1080, "Desktop"), { events++; true }, inputEnabled = false) }
        compose.onNodeWithTag("remote-display").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, events) }
    }
}
