package com.jhaago.cadagent.remote.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PairingReceipt(val requestId: String, val secret: String) { override fun toString() = "Pending pairing request" }
class RemotePairingClient(private val transport: RemoteTransport, private val store: RemoteCredentialStore) {
    suspend fun request(endpoint: RemoteEndpoint, secret: String, name: String): PairingReceipt {
        require(name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() })
        return parse {
            val response = transport.call(endpoint, RemoteOperation("pair/request", "POST", "Pairing", secret, mapOf("deviceName" to name)))
            val json = JSONObject(response.body)
            PairingReceipt(json.requiredString("requestId", 128), json.requiredString("receiptSecret", 256))
        }
    }
    suspend fun poll(endpoint: RemoteEndpoint, receipt: PairingReceipt): PairedWorkstation? = parse {
        val response = transport.call(endpoint, RemoteOperation("pair/status", "POST", "Receipt", receipt.secret, mapOf("requestId" to receipt.requestId)))
        val json = JSONObject(response.body)
        when (json.getString("state")) {
            "pending" -> null
            "approved" -> {
                val workstation = PairedWorkstation(endpoint, json.requiredString("deviceId", 128), json.requiredString("credential", 256))
                try { withContext(Dispatchers.IO) { store.write(workstation) } }
                catch (_: Exception) { throw RemoteFailure(0, "storage_failed", "Pairing could not be saved securely. Remove this device on Windows and try pairing again.") }
                workstation
            }
            else -> throw RemoteFailure(0, "pairing_invalid", "Pairing was rejected or expired. Open a new pairing window on Windows.")
        }
    }
    private suspend fun <T> parse(action: suspend () -> T): T = try { action() }
        catch (error: CancellationException) { throw error }
        catch (error: RemoteFailure) { throw error }
        catch (_: Exception) { throw RemoteFailure(0, "invalid_response", "The workstation returned an invalid pairing response.") }
}
internal fun JSONObject.requiredString(name: String, limit: Int): String =
    (get(name) as? String)?.takeIf { it.isNotBlank() && it.length <= limit && it.none(Char::isISOControl) }
        ?: throw RemoteFailure(0, "invalid_response", "The workstation returned an invalid response.")
