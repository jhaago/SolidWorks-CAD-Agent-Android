package com.jhaago.cadagent.remote.live

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PendingSubmissionStoreTest {
    private val scope = SubmissionScope("https://pc.example", "device")
    private val id = "11111111-1111-4111-8111-111111111111"
    private val next = "22222222-2222-4222-8222-222222222222"

    @Test fun recreationAndScopeIsolationAndCompareClear() = runTest {
        val backing = PreferenceFixture()
        val store = SharedPreferencesPendingSubmissionStore(backing.preferences, UnconfinedTestDispatcher(testScheduler))
        store.write(scope, PendingSubmission(id, true))
        val recreated = SharedPreferencesPendingSubmissionStore(backing.preferences, UnconfinedTestDispatcher(testScheduler))
        assertEquals(PendingSubmission(id, true), recreated.read(scope))
        assertNull(recreated.read(scope.copy(origin = "https://other.example")))
        assertNull(recreated.read(scope.copy(deviceId = "other")))
        recreated.clear(scope, id)
        recreated.write(scope, PendingSubmission(next, false))
        recreated.clear(scope, id)
        assertEquals(next, recreated.read(scope)?.id)
        val other = scope.copy(deviceId = "other")
        recreated.write(other, PendingSubmission(id, false))
        recreated.clearScope(scope)
        assertNull(recreated.read(scope)); assertEquals(id, recreated.read(other)?.id)
        val json = JSONObject(backing.values.values.single())
        assertEquals(setOf("version", "id", "cancelRequested"), json.keys().asSequence().toSet())
    }

    @Test fun failedCommitAndCorruptRecordSurfaceTrackingFailure() = runTest {
        val backing = PreferenceFixture()
        val store = SharedPreferencesPendingSubmissionStore(backing.preferences, UnconfinedTestDispatcher(testScheduler))
        backing.commitSucceeds = false
        try { store.write(scope, PendingSubmission(id, false)); fail("write must fail") } catch (_: SubmissionStorageException) { }
        backing.commitSucceeds = true; store.write(scope, PendingSubmission(id, false))
        val key = backing.values.keys.single()
        for (corrupt in listOf("{", """{"version":2,"id":"$id","cancelRequested":false}""", """{"version":1,"id":"bad","cancelRequested":false}""")) {
            backing.values[key] = corrupt
            try { store.read(scope); fail("corruption must not disappear") } catch (_: SubmissionStorageException) { }
        }
    }
}

/** In-memory boundary double; production serialization and commit result handling remain real. */
internal class PreferenceFixture {
    val values = mutableMapOf<String, String>()
    var commitSucceeds = true
    private fun editor(): SharedPreferences.Editor {
        val changes = mutableMapOf<String, String?>()
        return Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { changes[args!![0] as String] = args[1] as String?; proxy }
                "remove" -> { changes[args!![0] as String] = null; proxy }
                "commit" -> { if (commitSucceeds) changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }; commitSucceeds }
                else -> error("Unexpected editor method ${method.name}")
            }
        } as SharedPreferences.Editor
    }
    val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
        when (method.name) { "getString" -> values[args!![0] as String] ?: args[1]; "edit" -> editor(); else -> error("Unexpected preferences method ${method.name}") }
    } as SharedPreferences
}
