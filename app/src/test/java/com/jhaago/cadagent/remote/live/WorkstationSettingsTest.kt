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
        override fun read(endpoint: RemoteEndpoint) = record?.takeIf { it.endpoint == endpoint }
        override fun write(workstation: PairedWorkstation) { record = workstation }
        override fun delete(endpoint: RemoteEndpoint) { if (failDelete) error("private path"); record = null }
    }
    private class Server : RemoteTransport {
        var state = "pending"
        override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation) = RemoteResponse(200,
            if (operation.route == "pair/request") """{"requestId":"request","receiptSecret":"receipt"}"""
            else """{"state":"$state","deviceId":"device","credential":"credential"}""")
    }
    @Test fun pairingWaitsForWindowsApprovalThenSelectsLiveWithoutChangingCadJobs() = runTest {
        val app = AppContainer(); val jobs = app.repository; val server = Server(); val store = Store()
        val settings = WorkstationSettingsController(backgroundScope, app, server, store, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        assertTrue(settings.state.value.pairing); assertNull(store.record)
        server.state = "approved"; advanceTimeBy(1501); runCurrent()
        assertTrue(settings.state.value.paired); assertTrue(app.remoteAdapters.value.session.status.value.isLive)
        assertSame(jobs, app.repository); assertEquals("", settings.state.value.pairingSecret)
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
    @Test fun cancelledPairingCannotSwitchAdaptersAfterDemoWasSelected() = runTest {
        val app = AppContainer(); val server = Server(); val store = Store()
        val settings = WorkstationSettingsController(backgroundScope, app, server, store, storageDispatcher = StandardTestDispatcher(testScheduler))
        settings.changeEndpoint("https://pc.example"); settings.changePairingSecret("secret"); settings.pair(); runCurrent()
        settings.useDemo(); server.state = "approved"; advanceTimeBy(2000); runCurrent()
        assertFalse(app.remoteAdapters.value.session.status.value.isLive)
        assertFalse(settings.state.value.pairing)
    }
}
