package com.jhaago.cadagent.remote.live

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RemotePairingClientTest {
    private val endpoint = RemoteEndpoint.parse("https://pc.example")
    @Test fun failedWriteDoesNotMarkPaired() = runTest {
        val store = object : RemoteCredentialStore {
            override fun read(endpoint: RemoteEndpoint): PairedWorkstation? = null
            override fun write(workstation: PairedWorkstation) { error("private filesystem path") }
            override fun delete(endpoint: RemoteEndpoint) = Unit
        }
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation) = RemoteResponse(200, """{"state":"approved","deviceId":"device","credential":"private-secret"}""")
        }
        val client = RemotePairingClient(transport, store)
        try { client.poll(endpoint, PairingReceipt("request", "receipt")); fail("Expected persistence failure") }
        catch (error: RemoteFailure) { assertFalse(error.message!!.contains("private")) }
        assertNull(store.read(endpoint))
    }
    @Test fun pendingApprovalCannotIssueCredentials() = runTest {
        var writes = 0
        val store = object : RemoteCredentialStore {
            override fun read(endpoint: RemoteEndpoint): PairedWorkstation? = null
            override fun write(workstation: PairedWorkstation) { writes++ }
            override fun delete(endpoint: RemoteEndpoint) = Unit
        }
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation) = RemoteResponse(200, """{"state":"pending","deviceId":null,"credential":null}""")
        }
        assertNull(RemotePairingClient(transport, store).poll(endpoint, PairingReceipt("request", "receipt")))
        assertEquals(0, writes)
    }
    @Test fun cancelledSecureWriteRemainsCancellation() = runTest {
        val store = object : RemoteCredentialStore {
            override fun read(endpoint: RemoteEndpoint): PairedWorkstation? = null
            override fun write(workstation: PairedWorkstation) { throw kotlinx.coroutines.CancellationException("Cancelled") }
            override fun delete(endpoint: RemoteEndpoint) = Unit
        }
        val transport = object : RemoteTransport {
            override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation) = RemoteResponse(200, """{"state":"approved","deviceId":"device","credential":"secret"}""")
        }
        try { RemotePairingClient(transport, store).poll(endpoint, PairingReceipt("request", "receipt")); fail("Expected cancellation") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }
}
