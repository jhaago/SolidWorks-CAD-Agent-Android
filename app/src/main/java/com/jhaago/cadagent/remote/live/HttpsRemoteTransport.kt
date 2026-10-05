package com.jhaago.cadagent.remote.live

import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HttpsRemoteTransport(private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) : RemoteTransport {
    override suspend fun call(endpoint: RemoteEndpoint, operation: RemoteOperation): RemoteResponse = withContext(Dispatchers.IO) {
        val connection = open(endpoint.url(operation.route))
        try {
            val frame = operation.route == "display/frame"
            val limit = if (frame) 3 * 1024 * 1024 else 65536
            val deadline = System.nanoTime() + (if (frame) 5_000_000_000L else 2_000_000_000L)
            connection.connectTimeout = 2000
            connection.readTimeout = if (frame) 5000 else 2000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = operation.method
            connection.setRequestProperty("Authorization", "${operation.scheme} ${operation.secret}")
            connection.setRequestProperty("Accept", "application/json")
            operation.deviceCredential?.let { connection.setRequestProperty("X-Remote-Device-Credential", it) }
            if (operation.method == "POST") {
                val bytes = JSONObject(operation.payload).toString().toByteArray(Charsets.UTF_8)
                if (bytes.size > 65536) throw RemoteFailure(0, "request_large", "This remote request is too large.")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Do not display arbitrary server bodies, redirects or authentication values.
                val message = when (status) {
                    401, 403 -> "Remote authorization ended or pairing failed. Check Windows and reconnect."
                    409 -> "The workstation is busy or control changed. Check Windows and resume control."
                    429 -> "The workstation is busy. Wait briefly and reconnect."
                    else -> "The Windows remote request failed (HTTP $status). Check its control window."
                }
                throw RemoteFailure(status, "http_error", message)
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (System.nanoTime() > deadline) throw RemoteFailure(0, "timeout", "The workstation response timed out.")
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > limit) throw RemoteFailure(0, "response_large", "The workstation response is too large.")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            RemoteResponse(status, body)
        } catch (error: CancellationException) { throw error }
        catch (error: RemoteFailure) { throw error }
        catch (_: Exception) { throw RemoteFailure(0, "connection_failed", "Cannot reach the workstation securely. Check Tailscale and the Windows remote agent.") }
        finally { connection.disconnect() }
    }
}
