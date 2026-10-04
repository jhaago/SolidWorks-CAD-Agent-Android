package com.jhaago.cadagent.remote.data

/** Demo progression is separate from live session/provider interfaces. */
interface RemoteDemoDriver {
    fun finishConnecting(attempt: Long): Boolean
    fun advanceTask(id: String): Boolean
}
class FakeRemoteDemoDriver(
    private val session: FakeRemoteSessionRepository,
    private val ai: FakeAiControlRepository,
) : RemoteDemoDriver {
    override fun finishConnecting(attempt: Long) = session.finishConnecting(attempt)
    override fun advanceTask(id: String) = ai.advanceTask(id)
}
