package com.jhaago.cadagent.remote.model

data class RemoteWorkstationStatus(
    val connection: RemoteConnectionState = RemoteConnectionState.Disconnected,
    val workstationName: String = "No workstation paired",
    val mode: RemoteControlMode = RemoteControlMode.Manual,
    val controller: RemoteController = RemoteController.None,
    val task: AiTaskState = AiTaskState(),
    val protectedAction: ProtectedActionRequest? = null,
    val isLive: Boolean = false,
    val message: String? = null,
    val controlPending: Boolean = false,
    val agentHostAvailable: Boolean? = null,
    val supportsJobImages: Boolean = false,
    val executionMode: String? = null,
    val aiModel: String? = null,
    val solidWorksRunning: Boolean = false,
    val solidWorksAttached: Boolean = false,
    val solidWorksVisible: Boolean = false,
    val solidWorksVersion: String? = null,
    val activeDocument: String? = null,
) {
    init { require(connection == RemoteConnectionState.Connected || controller == RemoteController.None) }
}
