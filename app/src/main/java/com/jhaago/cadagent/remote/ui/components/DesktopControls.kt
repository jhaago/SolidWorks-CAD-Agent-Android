package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.input.*

@Composable
fun DesktopControls(control: Boolean, available: Boolean, onControl: (Boolean) -> Unit,
    cursorX: Float, cursorY: Float, onPointer: (RemotePointerEvent) -> Boolean,
    onKeyboard: (RemoteKeyboardEvent) -> Boolean) {
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!control, { onControl(false) }, label = { Text("View · pinch / pan") }, modifier = Modifier.testTag("desktop-view-mode"))
            FilterChip(control, { onControl(true) }, enabled = available, label = { Text("Control · touch / drag") }, modifier = Modifier.testTag("desktop-control-mode"))
            OutlinedButton(enabled = control && available, onClick = { onPointer(RemotePointerEvent(PointerAction.Click, cursorX, cursorY, PointerButton.Secondary)) }) { Text("Right click") }
            OutlinedButton(enabled = control && available, onClick = { onPointer(RemotePointerEvent(PointerAction.Scroll, cursorX, cursorY, scrollY = -1f)) }) { Text("Scroll ↑") }
            OutlinedButton(enabled = control && available, onClick = { onPointer(RemotePointerEvent(PointerAction.Scroll, cursorX, cursorY, scrollY = 1f)) }) { Text("Scroll ↓") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Escape", "Enter", "Tab", "Space", "Backspace", "F", "Left", "Right", "Up", "Down").forEach { key ->
                TextButton(enabled = control && available, onClick = {
                    if (onKeyboard(RemoteKeyboardEvent(key, KeyAction.Down))) onKeyboard(RemoteKeyboardEvent(key, KeyAction.Up))
                }) { Text(key) }
            }
            listOf("Z" to "Undo", "S" to "Save", "Y" to "Redo").forEach { (key, label) ->
                TextButton(enabled = control && available, onClick = {
                    if (onKeyboard(RemoteKeyboardEvent("Control", KeyAction.Down))) {
                        try {
                            if (onKeyboard(RemoteKeyboardEvent(key, KeyAction.Down))) onKeyboard(RemoteKeyboardEvent(key, KeyAction.Up))
                        } finally { onKeyboard(RemoteKeyboardEvent("Control", KeyAction.Up)) }
                    }
                }) { Text(label) }
            }
        }
    }
}
