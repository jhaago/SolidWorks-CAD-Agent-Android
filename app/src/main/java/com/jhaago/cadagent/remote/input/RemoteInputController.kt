package com.jhaago.cadagent.remote.input

interface RemoteInputController {
    fun sendPointer(event: RemotePointerEvent): Boolean
    fun sendKeyboard(event: RemoteKeyboardEvent): Boolean
    /** Live implementations must physically release every held key/button on authority loss. */
    fun releaseAll()
}
