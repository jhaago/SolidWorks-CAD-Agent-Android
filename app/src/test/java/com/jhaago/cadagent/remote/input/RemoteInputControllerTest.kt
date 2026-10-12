package com.jhaago.cadagent.remote.input

import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.model.*
import org.junit.Assert.*
import org.junit.Test

class RemoteInputControllerTest {
    @Test fun `input is accepted only with connected user authority`() {
        val session = FakeRemoteSessionRepository()
        val input = FakeRemoteInputController(session)
        val click = RemotePointerEvent(PointerAction.Click, .5f, .5f)
        assertFalse(input.sendPointer(click))
        val token = session.connect()!!
        assertFalse(input.sendPointer(click))
        session.finishConnecting(token)
        assertTrue(input.sendPointer(click))
        session.setMode(RemoteControlMode.Agent)
        val ai = FakeAiControlRepository(session)
        ai.submitTask("Inspect")
        assertFalse(input.sendPointer(click))
        assertFalse(input.sendKeyboard(RemoteKeyboardEvent("A", KeyAction.Down)))
        session.takeControl()
        assertTrue(input.sendPointer(click))
        session.disconnect()
        assertFalse(input.sendPointer(click))
        assertEquals(2, input.events.value.size)
    }

    @Test fun `invalid coordinates scroll and key identifiers are rejected without recording`() {
        val session = connected()
        val input = FakeRemoteInputController(session)
        for (v in listOf(-.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(input.sendPointer(RemotePointerEvent(PointerAction.Click, v, .5f)))
            assertFalse(input.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, v)))
        }
        assertFalse(input.sendPointer(RemotePointerEvent(PointerAction.Scroll, .5f, .5f, scrollY = Float.NaN)))
        assertFalse(input.sendKeyboard(RemoteKeyboardEvent("", KeyAction.Down)))
        assertFalse(input.sendKeyboard(RemoteKeyboardEvent("x".repeat(65), KeyAction.Down)))
        assertTrue(input.events.value.isEmpty())
    }

    @Test fun `drag scroll right click and keyboard events retain order and bounded history`() {
        val input = FakeRemoteInputController(connected())
        assertTrue(input.sendPointer(RemotePointerEvent(PointerAction.Down, 0f, 0f)))
        assertTrue(input.sendPointer(RemotePointerEvent(PointerAction.Move, .2f, .3f)))
        assertTrue(input.sendPointer(RemotePointerEvent(PointerAction.Up, 1f, 1f)))
        assertTrue(input.sendPointer(RemotePointerEvent(PointerAction.Click, .5f, .5f, button = PointerButton.Secondary)))
        assertTrue(input.sendPointer(RemotePointerEvent(PointerAction.Scroll, .5f, .5f, scrollY = -1f)))
        assertTrue(input.sendKeyboard(RemoteKeyboardEvent("Enter", KeyAction.Down)))
        assertTrue(input.sendKeyboard(RemoteKeyboardEvent("Enter", KeyAction.Up)))
        assertEquals(7, input.events.value.size)
        repeat(150) { input.sendPointer(RemotePointerEvent(PointerAction.Move, .5f, .5f)) }
        assertEquals(100, input.events.value.size)
        assertEquals(157L, input.events.value.last().sequence)
    }

    @Test fun `authority loss releases held buttons and keys before a new controller can act`() {
        val session = connected()
        val input = FakeRemoteInputController(session)
        input.sendPointer(RemotePointerEvent(PointerAction.Down, .5f, .5f))
        input.sendKeyboard(RemoteKeyboardEvent("Shift", KeyAction.Down))
        assertTrue(input.hasHeldInput)
        session.setMode(RemoteControlMode.Agent)
        assertFalse(input.hasHeldInput)
        session.takeControl()
        input.sendPointer(RemotePointerEvent(PointerAction.Down, .5f, .5f))
        session.disconnect()
        assertFalse(input.hasHeldInput)
    }

    @Test fun `aspect fit mapping rejects letterbox taps and maps portrait and landscape corners`() {
        val viewport = DisplayViewport.fit(1000f, 1000f, 1600, 900)
        assertNull(viewport.normalize(500f, 100f))
        assertEquals(NormalizedPoint(0f, 0f), viewport.normalize(0f, 218.75f))
        assertEquals(NormalizedPoint(1f, 1f), viewport.normalize(1000f, 781.25f))
        assertEquals(NormalizedPoint(.5f, .5f), viewport.normalize(500f, 500f))
        val portrait = DisplayViewport.fit(1200f, 600f, 600, 1200)
        assertNull(portrait.normalize(10f, 300f))
        assertEquals(NormalizedPoint(.5f, .5f), portrait.normalize(600f, 300f))
        assertNull(DisplayViewport.fit(0f, 0f, 0, 0).normalize(0f, 0f))
    }

    private fun connected() = FakeRemoteSessionRepository().also { it.finishConnecting(it.connect()!!) }
}
