package com.vpntz.app.tunnel.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsttBridgeArgsTest {

    private fun config(
        domain: String = "t.example.com",
        publicKey: String = "abc",
        noizdns: Boolean = false,
        noizStealth: Boolean = false
    ) = TunnelAdapterConfig.Dnstt(
        domain = domain,
        publicKey = publicKey,
        authoritative = true,
        resolvers = emptyList(),
        effectiveDnsServer = "1.1.1.1:53",
        listenPort = 1081,
        listenHost = "127.0.0.1",
        maxPayload = 1234,
        resolverMode = "roundrobin",
        rrSpreadCount = 2,
        noizdns = noizdns,
        noizStealth = noizStealth,
        socksProxyAddr = "127.0.0.1:1080",
        socksProxyUser = "u",
        socksProxyPass = "p"
    )

    @Test
    fun `resolves every dnstt field to the bridge args`() {
        val a = DnsttBridgeArgs.resolve(config())
        assertEquals("1.1.1.1:53", a.dnsServer)
        assertEquals("t.example.com", a.tunnelDomain)
        assertEquals("abc", a.publicKey)
        assertEquals(1081, a.listenPort)
        assertEquals("127.0.0.1", a.listenHost)
        assertTrue(a.authoritativeMode)
        assertFalse(a.noizMode)
        assertFalse(a.stealthMode)
        assertEquals(1234, a.maxPayload)
        assertEquals("roundrobin", a.resolverMode)
        assertEquals(2, a.rrSpreadCount)
        assertEquals("127.0.0.1:1080", a.socksProxyAddr)
        assertEquals("u", a.socksProxyUser)
        assertEquals("p", a.socksProxyPass)
    }

    @Test
    fun `resolves noiz flags onto the bridge args`() {
        val a = DnsttBridgeArgs.resolve(config(noizdns = true, noizStealth = true))
        assertTrue(a.noizMode)
        assertTrue(a.stealthMode)
    }
}
