package com.jhaago.cadagent.remote.input

enum class KeyAction { Down, Up }
data class RemoteKeyboardEvent(val key: String, val action: KeyAction) : RemoteInputEvent {
    val valid: Boolean get() = key.isNotBlank() && key.length <= 64 && key.none { it.isISOControl() }
}
