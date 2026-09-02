package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.DnsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipstreamBridgeArgsTest {

    private fun config(domain: String = "kitonga.example.com", debugLogging: Boolean = false) =
        TunnelAdapterConfig.Slipstream(
            domain = domain,
            resolvers = listOf(DnsResolver("1.1.1.1", 53, authoritative = true), DnsResolver("1.1.1.1", 53, authoritative = true)),
            listenPort = 1080,
            listenHost = "127.0.0.1",
            congestionControl = "bbr",
            keepAliveInterval = 5000,
            gsoEnabled = true,
            debugLogging = debugLogging,
            idlePollIntervalMs = 10000,
            idleTimeoutMs = 120000
        )

    @Test
    fun `resolves slipstream config to bridge args`() {
        val a = SlipstreamBridgeArgs.resolve(config())
        assertEquals("kitonga.example.com", a.domain)
        // resolvers deduped by host:port
        assertEquals(1, a.resolvers.size)
        assertEquals("1.1.1.1", a.resolvers[0].host)
        assertEquals(53, a.resolvers[0].port)
        assertTrue(a.resolvers[0].authoritative)
        assertEquals("bbr", a.congestionControl)
        assertEquals(5000, a.keepAliveInterval)
        assertEquals(1080, a.tcpListenPort)
        assertEquals("127.0.0.1", a.tcpListenHost)
        assertTrue(a.gsoEnabled)
        assertEquals(10000, a.idlePollIntervalMs)
        assertEquals(120000, a.idleTimeoutMs)
    }

    @Test
    fun `debug logging maps to poll and stream debug flags`() {
        val a = SlipstreamBridgeArgs.resolve(config(debugLogging = true))
        assertTrue(a.debugPoll)
        assertTrue(a.debugStreams)
    }
}
