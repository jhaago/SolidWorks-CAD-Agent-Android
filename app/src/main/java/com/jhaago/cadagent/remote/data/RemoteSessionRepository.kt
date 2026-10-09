package com.jhaago.cadagent.remote.data

import com.jhaago.cadagent.remote.model.*
import kotlinx.coroutines.flow.StateFlow

interface RemoteSessionRepository {
    val status: StateFlow<RemoteWorkstationStatus>
    /** Returns the attempt token, or null when already connecting/connected. */
    fun connect(): Long?
    fun disconnect()
    fun setMode(mode: RemoteControlMode)
    fun takeControl()
}
