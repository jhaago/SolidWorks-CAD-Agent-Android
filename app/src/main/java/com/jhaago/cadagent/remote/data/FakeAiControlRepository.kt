package com.jhaago.cadagent.remote.data

/** No provider, network, desktop input or printer integration. Progress advances only explicitly. */
class FakeAiControlRepository(private val session: FakeRemoteSessionRepository) : AiControlRepository {
    override val status = session.status
    override fun submitTask(instruction: String) = session.submitTask(instruction)
    override fun stopTask() = session.stopTask()
    override fun approveProtectedAction(id: String) = session.decideProtectedAction(id, true)
    override fun rejectProtectedAction(id: String) = session.decideProtectedAction(id, false)
    fun advanceTask(id: String) = session.advanceTask(id)
}
