package com.jhaago.cadagent.remote.live

interface RemoteTransport { suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse }
class RemoteOperation(
    val route: String, val method: String, val scheme: String, val secret: String,
    val payload: Map<String, Any?> = emptyMap(), val deviceCredential: String? = null,
) {
    init {
        require(method == "POST" || method == "GET")
        require(scheme in setOf("Pairing", "Receipt", "Device", "Session"))
        require(secret.length in 1..256 && secret.none { it.isISOControl() })
        require(deviceCredential == null || (deviceCredential.length in 1..256 && deviceCredential.none { it.isISOControl() }))
    }
    override fun toString() = "Remote operation ($method $route)"
}
class RemoteResponse(val status: Int, val body: String) { override fun toString() = "Remote response (HTTP $status)" }
class RemoteFailure(val status: Int, val code: String, message: String) : Exception(message)

interface RemoteCredentialStore {
    fun read(endpoint: RemoteEndpoint): PairedWorkstation?
    fun write(workstation: PairedWorkstation)
    fun delete(endpoint: RemoteEndpoint)
}
