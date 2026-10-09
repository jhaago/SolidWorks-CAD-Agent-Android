package com.jhaago.cadagent.remote.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.input.PointerAction
import com.jhaago.cadagent.remote.ui.components.RemoteDisplaySurface
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RemoteGestureCancellationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun removingTheSurfaceDuringADragSendsUpAndReleasesAuthority() {
        val actions = mutableListOf<PointerAction>(); var releases = 0
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) RemoteDisplaySurface(RemoteDisplayFrame(100, 60, "Test"), { actions += it.action; true }, onCancelled = { releases++ })
        }
        compose.onNodeWithTag("remote-display").performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(listOf(PointerAction.Down), actions); visible.value = false }
        compose.runOnIdle { assertEquals(listOf(PointerAction.Down, PointerAction.Up), actions); assertEquals(1, releases) }
    }
}
