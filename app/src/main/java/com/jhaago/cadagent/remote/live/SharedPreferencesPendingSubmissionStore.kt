package com.jhaago.cadagent.remote.live

import android.content.SharedPreferences
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SharedPreferencesPendingSubmissionStore(
    private val preferences: SharedPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PendingSubmissionStore {
    private fun key(scope: SubmissionScope): String {
        val encoded = JSONObject().put("origin", scope.origin).put("device", scope.deviceId).toString().toByteArray(Charsets.UTF_8)
        return "submission-" + MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
    }
    private fun load(key: String): PendingSubmission? {
        val value = preferences.getString(key, null) ?: return null
        val json = JSONObject(value)
        require(json.get("version") == 1 && json.get("cancelRequested") is Boolean)
        return PendingSubmission(json.getString("id"), json.getBoolean("cancelRequested"))
    }
    private suspend fun <T> stored(action: () -> T): T = withContext(dispatcher) {
        try { synchronized(preferences) { action() } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw SubmissionStorageException() }
    }
    override suspend fun read(scope: SubmissionScope): PendingSubmission? = stored { load(key(scope)) }
    override suspend fun write(scope: SubmissionScope, submission: PendingSubmission): Unit = stored {
        val key = key(scope)
        val existing = load(key)
        // A delayed writer cannot replace another submission or undo a persisted Stop.
        require(existing == null || existing.id == submission.id)
        val json = JSONObject().put("version", 1).put("id", submission.id)
            .put("cancelRequested", submission.cancelRequested || existing?.cancelRequested == true)
        check(preferences.edit().putString(key, json.toString()).commit())
    }
    override suspend fun clear(scope: SubmissionScope, expectedId: String): Unit = stored {
        val key = key(scope)
        if (load(key)?.id == expectedId) check(preferences.edit().remove(key).commit())
    }
    override suspend fun clearScope(scope: SubmissionScope): Unit = stored {
        check(preferences.edit().remove(key(scope)).commit())
    }
}
