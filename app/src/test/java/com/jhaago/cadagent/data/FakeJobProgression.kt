package com.jhaago.cadagent.data

import com.jhaago.cadagent.model.CadJob

interface FakeJobProgression {
    suspend fun advanceJob(jobId: String): Result<CadJob>
}
