package com.vpntz.app.tunnel.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class NaiveBridgeArgsTest {

    private val config = TunnelAdapterConfig.Naive(
        host = "caddy.example.com",
        port = 443,
        username = "user",
        password = "pass",
        listenPort = 1080,
        listenHost = "127.0.0.1"
    )

    @Test
    fun `resolves naive config to bridge args without context`() {
        val a = NaiveBridgeArgs.resolve(config)
        assertEquals("caddy.example.com", a.serverHost)
        assertEquals(443, a.serverPort)
        assertEquals("user", a.username)
        assertEquals("pass", a.password)
        assertEquals(1080, a.listenPort)
        assertEquals("127.0.0.1", a.listenHost)
    }
}
