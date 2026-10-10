package com.jhaago.cadagent.remote.live

import org.junit.Assert.*
import org.junit.Test

class RemoteEndpointTest {
    @Test fun endpointSupportsOnlyPublishedRemoteRoutes() {
        val endpoint = RemoteEndpoint.parse("https://pc.example")
        val job = "11111111-1111-1111-1111-111111111111"
        val routes = listOf("pair/request", "pair/status", "session/create", "session/renew", "session/status",
            "session/heartbeat", "session/resume", "session/take-control", "session/release", "session/close",
            "display/frame", "input", "agent/status", "agent/jobs", "agent/jobs/$job") +
            listOf("approve", "request-changes", "complete", "cancel", "artifact").map { "agent/jobs/$job/$it" }
        routes.forEach { route -> assertEquals("https://pc.example/remote/v1/$route", endpoint.url(route).toString()) }
    }
    @Test fun endpointRejectsUnpublishedAndAmbiguousRoutes() {
        val endpoint = RemoteEndpoint.parse("https://pc.example")
        listOf("unknown/route", "session/delete", "agent/settings", "agent/jobs/not-a-guid",
            "agent/jobs/11111111-1111-1111-1111-111111111111/delete", "agent/jobs/1-1-1-1-1",
            "agent/jobs/11111111-1111-1111-1111-111111111111/artifact/extra", "../input", "/input",
            "input?token=secret", "input#fragment", "agent/jobs/%2e%2e/approve").forEach { route ->
            assertThrows(IllegalArgumentException::class.java) { endpoint.url(route) }
        }
    }
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
