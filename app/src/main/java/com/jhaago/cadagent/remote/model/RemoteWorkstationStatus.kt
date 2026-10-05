package com.jhaago.cadagent.remote.model

data class RemoteWorkstationStatus(
    val connection: RemoteConnectionState = RemoteConnectionState.Disconnected,
    val workstationName: String = "Demo workstation",
    val mode: RemoteControlMode = RemoteControlMode.Manual,
    val controller: RemoteController = RemoteController.None,
    val task: AiTaskState = AiTaskState(),
    val protectedAction: ProtectedActionRequest? = null,
    val isLive: Boolean = false,
    val message: String? = null,
    val controlPending: Boolean = false,
) {
    init { require(connection == RemoteConnectionState.Connected || controller == RemoteController.None) }
}
