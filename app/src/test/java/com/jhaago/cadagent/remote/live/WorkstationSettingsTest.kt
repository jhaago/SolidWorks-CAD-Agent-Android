package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.di.AppContainer
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkstationSettingsTest {
    private class Store : RemoteCredentialStore {
        var record: PairedWorkstation? = null
        var failDelete = false
        var onDelete: (() -> Unit)? = null
        override fun read(endpoint: RemoteEndpoint) = record?.takeIf { it.endpoint == endpoint }
        override fun write(workstation: PairedWorkstation) { record = workstation }
        override fun delete(endpoint: RemoteEndpoint) { if (failDelete) error("private path"); record = null; onDelete?.invoke() }
    }
    private class Server : RemoteTransport {
        var state = "pending"
        var transientPollFailures = 0
        var authorizationFailure = false
        var pollCount = 0
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse {
            if (operation.route == "pair/status") {
                pollCount++
                if (authorizationFailure) throw RemoteFailure(401, "http_error", "Pairing authorization ended.")
                if (transientPollFailures-- > 0) throw RemoteFailure(0, "connection_failed", "Temporary secure connection failure.")
            }
            return RemoteResponse(200, if (operation.route == "pair/request") """{"requestId":"request","receiptSecret":"receipt"}"""
                else """{"state":"$state","deviceId":"device","credential":"credential"}""")
        }
    }
    @Test fun pairingWaitsForWindowsApprovalThenSelectsLiveWithoutChangingCadJobs() = runTest {
        val app = AppContainer(); val server = Server(); val store = Store()
        val settings = WorkstationSettingsController(backgroundScope, app, server, store, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        assertTrue(settings.state.value.pairing); assertNull(store.record)
        server.state = "approved"; advanceTimeBy(1501); runCurrent()
        assertTrue(settings.state.value.paired); assertTrue(app.remoteAdapters.value.session.status.value.isLive)
        assertEquals("", settings.state.value.pairingSecret)
    }
    @Test fun rejectedPairingStaysDisconnectedAndClearsSecret() = runTest {
        val settings = WorkstationSettingsController(backgroundScope, AppContainer(), Server().apply { state = "rejected" }, Store(), storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        assertFalse(settings.state.value.pairing); assertFalse(settings.state.value.paired)
        assertEquals("", settings.state.value.pairingSecret); assertNotNull(settings.state.value.error)
    }
    @Test fun failedForgetDoesNotClaimCredentialsWereDeleted() = runTest {
        val store = Store().apply { record = PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential"); failDelete = true }
        val settings = WorkstationSettingsController(backgroundScope, AppContainer(), Server(), store, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.useSaved(); runCurrent()
        assertTrue(settings.state.value.paired)
        settings.forget(); runCurrent()
        assertTrue(settings.state.value.paired); assertNotNull(settings.state.value.error)
        assertFalse(settings.state.value.error!!.contains("private"))
    }
    @Test fun cancelledPairingCannotSelectAWorkstationAfterCancellation() = runTest {
        val app = AppContainer(); val server = Server(); val store = Store()
        val settings = WorkstationSettingsController(backgroundScope, app, server, store, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        settings.cancelPairing(); server.state = "approved"; advanceTimeBy(2000); runCurrent()
        assertFalse(app.remoteAdapters.value.session.status.value.isLive)
        assertFalse(settings.state.value.pairing)
    }
    @Test fun transientSecurePollFailureDoesNotDiscardApprovedPairing() = runTest {
        val app = AppContainer(); val server = Server().apply { transientPollFailures = 1 }; val store = Store()
        val settings = WorkstationSettingsController(backgroundScope, app, server, store,
            storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        assertTrue(settings.state.value.pairing)
        assertNull(settings.state.value.error)
        server.state = "approved"; advanceTimeBy(1501); runCurrent()
        assertTrue(settings.state.value.paired)
        assertEquals(2, server.pollCount)
        assertEquals("device", store.record?.deviceId)
    }
    @Test fun pairingAuthorizationFailureIsNotRetried() = runTest {
        val server = Server().apply { authorizationFailure = true }
        val settings = WorkstationSettingsController(backgroundScope, AppContainer(), server, Store(),
            storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        advanceTimeBy(5000); runCurrent()
        assertFalse(settings.state.value.pairing)
        assertEquals(1, server.pollCount)
        assertNotNull(settings.state.value.error)
    }
    @Test fun persistentSecurePollFailureStopsAtPairingDeadline() = runTest {
        val app = AppContainer(); val server = Server().apply { transientPollFailures = Int.MAX_VALUE }; val store = Store()
        val settings = WorkstationSettingsController(backgroundScope, app, server, store,
            storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        advanceTimeBy(120001); runCurrent()
        assertFalse(settings.state.value.pairing)
        assertFalse(settings.state.value.paired)
        assertNull(store.record)
        assertEquals("Pairing expired. Open a new pairing window on Windows.", settings.state.value.error)
        assertTrue(server.pollCount > 1)
    }
    @Test fun forgetClearsSelectedWorkstationAndSavedOriginWhenDisconnected() = runTest {
        val app = AppContainer()
        val store = Store().apply { record = PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential") }
        var savedOrigin = "https://pc.example"
        val settings = WorkstationSettingsController(backgroundScope, app, Server(), store,
            saveOrigin = { savedOrigin = it }, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint(savedOrigin); settings.useSaved(); runCurrent()
        assertTrue(settings.state.value.paired)

        settings.forget(); runCurrent()

        assertFalse(settings.state.value.paired)
        assertFalse(app.remoteAdapters.value.session.status.value.isLive)
        assertNull(store.record)
        assertEquals("", savedOrigin)
        assertNull(settings.state.value.error)
    }
    @Test fun forgetDoesNotClaimDisconnectWhenRemoteStartsDuringCredentialDeletion() = runTest {
        val app = AppContainer()
        val store = Store().apply { record = PairedWorkstation(RemoteEndpoint.parse("https://pc.example"), "device", "credential") }
        var savedOrigin = "https://pc.example"
        val settings = WorkstationSettingsController(backgroundScope, app, Server(), store,
            saveOrigin = { savedOrigin = it }, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint(savedOrigin); settings.useSaved(); runCurrent()
        assertTrue(settings.state.value.paired)
        store.onDelete = {
            app.remoteAdapters.value.driver!!.setForeground(true)
            assertNotNull(app.remoteAdapters.value.session.connect())
        }

        settings.forget(); runCurrent()

        assertTrue(settings.state.value.paired)
        assertTrue(app.remoteAdapters.value.session.status.value.isLive)
        assertEquals("https://pc.example", savedOrigin)
        assertNotNull(settings.state.value.error)
        assertFalse(settings.state.value.message?.contains("removed") ?: false)
    }
}
