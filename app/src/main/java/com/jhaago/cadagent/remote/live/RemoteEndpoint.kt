package com.jhaago.cadagent.remote.live

import java.net.URI
import java.net.URL
import java.util.Locale

class RemoteEndpoint private constructor(val origin: String) {
    fun url(route: String): URL {
        require(route.matches(Regex("[a-z-]+/[a-z-]+")) || route == "input") { "Unsupported remote route" }
        return URL("$origin/remote/v1/$route")
    }
    override fun toString() = origin
    override fun equals(other: Any?) = other is RemoteEndpoint && origin == other.origin
    override fun hashCode() = origin.hashCode()
    companion object {
        fun parse(value: String): RemoteEndpoint {
            try {
                require(value.length <= 512)
                val uri = URI(value.trim())
                require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
                require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
                require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
                require(uri.port == -1 || uri.port in 1..65535)
                val host = uri.host.lowercase(Locale.ROOT)
                val authority = host + if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
                return RemoteEndpoint("https://$authority")
            } catch (_: Exception) { throw IllegalArgumentException("Enter a valid HTTPS workstation address without credentials or extra paths.") }
        }
    }
}

class PairedWorkstation(val endpoint: RemoteEndpoint, val deviceId: String, val credential: String) {
    init { require(deviceId.length in 1..128 && credential.length in 1..256) }
    fun matches(endpoint: RemoteEndpoint) = this.endpoint == endpoint
    override fun toString() = "Paired workstation (${endpoint.origin})"
}
