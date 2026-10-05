package com.jhaago.cadagent.remote.live

import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class KeystoreRemoteCredentialStoreTest {
    @Test fun credentialRoundTripAndDeletionUseEncryptedNoBackupStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.noBackupFilesDir.listFiles()?.toSet().orEmpty()
        val endpoint = RemoteEndpoint.parse("https://${UUID.randomUUID()}.example")
        val credential = "private-test-credential-${UUID.randomUUID()}"
        val store = KeystoreRemoteCredentialStore(context)
        try {
            store.write(PairedWorkstation(endpoint, "device", credential))
            assertEquals(credential, KeystoreRemoteCredentialStore(context).read(endpoint)!!.credential)
            val written = context.noBackupFilesDir.listFiles()!!.toSet() - before
            assertEquals(1, written.size)
            assertFalse(written.single().readText().contains(credential))
            store.delete(endpoint)
            assertNull(store.read(endpoint))
        } finally { store.delete(endpoint) }
    }
    @Test fun corruptedCredentialRecordFailsClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.noBackupFilesDir.listFiles()?.toSet().orEmpty()
        val endpoint = RemoteEndpoint.parse("https://${UUID.randomUUID()}.example")
        val store = KeystoreRemoteCredentialStore(context)
        try {
            store.write(PairedWorkstation(endpoint, "device", "credential"))
            val written = context.noBackupFilesDir.listFiles()!!.toSet() - before
            written.single().writeText("corrupted")
            try { store.read(endpoint); fail("Expected encrypted storage failure") }
            catch (error: RemoteFailure) { assertFalse(error.message!!.contains("corrupted")) }
        } finally { store.delete(endpoint) }
    }
    @Test fun deniedDeletionDoesNotReportCredentialRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.noBackupFilesDir
        val endpoint = RemoteEndpoint.parse("https://" + UUID.randomUUID() + ".example")
        val store = KeystoreRemoteCredentialStore(context)
        store.write(PairedWorkstation(endpoint, "device", "credential"))
        try {
            assertTrue(directory.setWritable(false, false))
            assertFalse("Fixture must deny deletion", directory.canWrite())
            var failure: RemoteFailure? = null
            try { store.delete(endpoint) } catch (error: RemoteFailure) { failure = error }
            assertNotNull("A denied deletion must fail instead of claiming success", failure)
            assertEquals("device", store.read(endpoint)!!.deviceId)
        } finally {
            assertTrue(directory.setWritable(true, true))
            store.delete(endpoint)
        }
    }

}
