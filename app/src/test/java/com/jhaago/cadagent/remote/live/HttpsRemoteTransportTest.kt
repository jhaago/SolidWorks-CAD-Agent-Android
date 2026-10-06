package com.jhaago.cadagent.remote.live

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class HttpsRemoteTransportTest {
    private class Connection(url: URL, val code: Int = 200, val text: String = "{}") : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(text.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(text.toByteArray())
        override fun getOutputStream() = sent
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    @Test fun lifecycleReadWindowDoesNotRelaxUrgentControlWindow() = runTest {
        val control = Connection(URL("https://pc.example/remote/v1/input"))
        HttpsRemoteTransport { control }.call(RemoteEndpoint.parse("https://pc.example"), RemoteOperation("input", "POST", "Session", "token"))
        assertEquals(2000, control.readTimeout)
        val planning = Connection(URL("https://pc.example/remote/v1/agent/jobs"))
        HttpsRemoteTransport { planning }.call(RemoteEndpoint.parse("https://pc.example"), RemoteOperation("agent/jobs", "POST", "Session", "token"))
        assertEquals(15000, planning.readTimeout)
    }
    @Test fun authorizationIsOnlyInHeadersAndRedirectsAreDisabled() = runTest {
        val connection = Connection(URL("https://pc.example/remote/v1/input"))
        val transport = HttpsRemoteTransport { connection }
        transport.call(RemoteEndpoint.parse("https://pc.example"), RemoteOperation("input", "POST", "Session", "private-token", mapOf("kind" to "move")))
        assertEquals("Session private-token", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.url.toString().contains("private-token"))
        assertFalse(connection.sent.toString().contains("private-token"))
    }
    @Test fun redirectAndErrorsAreSanitizedAndNeverRetried() = runTest {
        var calls = 0
        val transport = HttpsRemoteTransport { calls++; Connection(it, 302, "private-secret") }
        try { transport.call(RemoteEndpoint.parse("https://pc.example"), RemoteOperation("input", "POST", "Session", "token")); fail("Expected redirect failure") }
        catch (error: RemoteFailure) { assertFalse(error.message!!.contains("private-secret")) }
        assertEquals(1, calls)
    }
    @Test fun oversizedResponseIsRejected() = runTest {
        val transport = HttpsRemoteTransport { Connection(it, 200, "x".repeat(65537)) }
        try { transport.call(RemoteEndpoint.parse("https://pc.example"), RemoteOperation("session/status", "GET", "Session", "token")); fail("Expected size failure") }
        catch (error: RemoteFailure) { assertFalse(error.message!!.contains("xxx")) }
    }
}
