package com.jhaago.cadagent.remote.live

import com.jhaago.cadagent.di.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkstationSettingsState(val endpoint: String = "", val pairingSecret: String = "", val pairing: Boolean = false,
    val paired: Boolean = false, val error: String? = null, val message: String? = null) {
    override fun toString() = "Workstation settings (pairing=$pairing, paired=$paired)"
}
class WorkstationSettingsController(
    private val scope: CoroutineScope, private val container: AppContainer, private val transport: RemoteTransport,
    private val store: RemoteCredentialStore, initialOrigin: String = "", private val saveOrigin: (String) -> Unit = {},
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutable = MutableStateFlow(WorkstationSettingsState(endpoint = initialOrigin))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var revision = 0L
    private val pairingClient = RemotePairingClient(transport, store, storageDispatcher)
    private fun editable(): Boolean {
        if (container.canSwitchRemote()) return true
        mutable.value = mutable.value.copy(error = "Disconnect Remote before changing the workstation."); return false
    }
    fun changeEndpoint(value: String) {
        if (!editable()) return
        cancelPairing(); mutable.value = mutable.value.copy(endpoint = value.take(2048), paired = false, error = null, message = null)
    }
    fun changePairingSecret(value: String) {
        if (!mutable.value.pairing) mutable.value = mutable.value.copy(pairingSecret = value.take(256), error = null)
    }
    fun cancelPairing() { revision++; job?.cancel(); job = null; mutable.value = mutable.value.copy(pairing = false, pairingSecret = "", message = null) }
    fun useDemo() { if (!editable()) return; cancelPairing(); container.selectDemo(); mutable.value = mutable.value.copy(error = null, message = "Demo selected. CAD jobs are simulated.") }
    fun pair() {
        if (!editable() || mutable.value.pairing) return
        val endpoint = endpoint() ?: return
        val secret = mutable.value.pairingSecret
        cancelPairing(); val id = revision
        mutable.value = mutable.value.copy(pairing = true, paired = false, error = null, message = "Accept this device in the Windows Remote Agent window.")
        job = scope.launch {
            try {
                val receipt = pairingClient.request(endpoint, secret, "Android CAD Agent")
                withTimeout(120000) {
                    while (isActive && id == revision) {
                        val record = pairingClient.poll(endpoint, receipt)
                        if (record != null) { if (id == revision) activate(record); break }
                        delay(1500)
                    }
                }
            } catch (error: TimeoutCancellationException) { if (id == revision) failure("Pairing expired. Open a new pairing window on Windows.") }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (id == revision) failure(safe(error)) }
            finally { if (id == revision) mutable.value = mutable.value.copy(pairing = false, pairingSecret = "") }
        }
    }
    fun useSaved() {
        if (!editable()) return
        val endpoint = endpoint() ?: return
        cancelPairing(); val id = revision
        job = scope.launch {
            try {
                val record = withContext(storageDispatcher) { store.read(endpoint) }
                if (id == revision) { if (record == null) failure("No saved pairing for this address. Pair with Windows first.") else activate(record) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (id == revision) failure(safe(error)) }
        }
    }
    fun forget() {
        if (!editable()) return
        val endpoint = endpoint() ?: return
        cancelPairing(); val id = revision
        job = scope.launch {
            try {
                withContext(storageDispatcher) { store.delete(endpoint) }
                if (id == revision) {
                    container.selectDemo(); saveOrigin("")
                    mutable.value = mutable.value.copy(paired = false, error = null, message = "Saved pairing removed. Remove this Android device in Windows too.")
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (id == revision) failure("Saved pairing could not be removed. Try again before sharing this phone.") }
        }
    }
    private fun activate(record: PairedWorkstation) {
        val driver = LiveConnectionDriver(scope, transport, record)
        if (!container.selectLive(driver)) { driver.close(); failure("Disconnect Remote before changing the workstation."); return }
        saveOrigin(record.endpoint.origin)
        mutable.value = mutable.value.copy(endpoint = record.endpoint.origin, paired = true, pairingSecret = "", error = null, message = "Live workstation selected. Open Remote to connect in view-only mode.")
    }
    private fun endpoint(): RemoteEndpoint? = try { RemoteEndpoint.parse(mutable.value.endpoint.trim()) }
        catch (_: Exception) { failure("Enter an HTTPS workstation address, for example https://pc.example.ts.net."); null }
    private fun failure(message: String) { mutable.value = mutable.value.copy(error = message, message = null) }
    private fun safe(error: Exception) = if (error is RemoteFailure) error.message!! else "The workstation could not be paired. Check the Windows Remote Agent window."
}
