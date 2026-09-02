package com.vpntz.app.tunnel.adapter

import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DohBridgeArgsTest {

    @Test
    fun `resolves doh config to bridge args`() {
        val cfg = TunnelAdapterConfig.Doh(
            url = "https://dns.google/dns-query",
            listenPort = 1080,
            listenHost = "127.0.0.1",
            localAuthUsername = "u",
            localAuthPassword = "p",
            upstreamSocksAddr = InetSocketAddress("10.0.0.1", 1080)
        )
        val a = DohBridgeArgs.resolve(cfg)
        assertEquals("https://dns.google/dns-query", a.dohUrl)
        assertEquals(1080, a.listenPort)
        assertEquals("127.0.0.1", a.listenHost)
        assertEquals("u", a.localAuthUsername)
        assertEquals("p", a.localAuthPassword)
        assertEquals("10.0.0.1", a.upstreamSocksAddr!!.hostString)
    }

    @Test
    fun `auth and upstream are null when not chained`() {
        val cfg = TunnelAdapterConfig.Doh(
            url = "https://dns.google/dns-query",
            listenPort = 1080,
            listenHost = "127.0.0.1"
        )
        val a = DohBridgeArgs.resolve(cfg)
        assertNull(a.localAuthUsername)
        assertNull(a.upstreamSocksAddr)
    }
}
