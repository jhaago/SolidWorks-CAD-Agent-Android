package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.remote.data.*
import com.jhaago.cadagent.remote.display.RemoteDisplaySource
import com.jhaago.cadagent.remote.input.*
import com.jhaago.cadagent.remote.model.RemoteControlMode

class LiveRemoteSessionRepository(private val driver: LiveConnectionDriver) : RemoteSessionRepository {
    override val status = driver.status
    override fun connect() = driver.connect()
    override fun disconnect() = driver.disconnect()
    override fun takeControl() = driver.resumeControl()
    override fun setMode(mode: RemoteControlMode) = driver.setMode(mode)
    fun setForeground(value: Boolean) = driver.setForeground(value)
}

class LiveRemoteDisplaySource(driver: LiveConnectionDriver) : RemoteDisplaySource { override val frame = driver.frame }

class LiveRemoteInputController(private val driver: LiveConnectionDriver) : RemoteInputController {
    override fun sendPointer(event: RemotePointerEvent) = driver.sendPointer(event)
    override fun sendKeyboard(event: RemoteKeyboardEvent) = driver.sendKeyboard(event)
    override fun releaseAll() = driver.releaseAll()
}

class LiveAiControlRepository(private val driver: LiveConnectionDriver) : AiControlRepository {
    override val status = driver.status
    override fun submitTask(instruction: String) = driver.submitAiTask(instruction)
    override fun stopTask() = driver.stopAiTask()
    override fun approvePlan() = driver.approvePlan()
    override fun requestChanges(instructions: String) = driver.requestChanges(instructions)
    override fun completeTask() = driver.completeTask()
    override suspend fun downloadArtifact() = driver.downloadArtifact()
    override fun approveProtectedAction(id: String) = false
    override fun rejectProtectedAction(id: String) = false
}
