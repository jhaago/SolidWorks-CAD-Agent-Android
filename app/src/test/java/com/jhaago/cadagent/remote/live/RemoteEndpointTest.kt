package com.jhaago.cadagent.remote.live

import org.junit.Assert.*
import org.junit.Test

class RemoteEndpointTest {
    @Test fun endpointRejectsUnsafeAndMalformedUrls() {
        listOf("http://pc", "https://user:password@pc", "https://pc/?token=secret", "https://pc/#secret", "https://pc/other", "https://", "https://pc:99999").forEach {
            assertThrows(IllegalArgumentException::class.java) { RemoteEndpoint.parse(it) }
        }
        assertEquals("https://pc.example", RemoteEndpoint.parse("https://PC.example:443/").origin)
        assertEquals("https://pc.example/remote/v1/input", RemoteEndpoint.parse("https://pc.example").url("input").toString())
    }
    @Test fun storedCredentialCannotFollowChangedOrigin() {
        val stored = PairedWorkstation(RemoteEndpoint.parse("https://first.example"), "device", "credential")
        assertFalse(stored.matches(RemoteEndpoint.parse("https://other.example")))
        assertFalse(stored.toString().contains("credential"))
        assertFalse(RemoteOperation("input", "POST", "Session", "private-token").toString().contains("private-token"))
    }
}
