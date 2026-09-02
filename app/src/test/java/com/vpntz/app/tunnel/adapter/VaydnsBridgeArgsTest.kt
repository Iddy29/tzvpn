package com.vpntz.app.tunnel.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaydnsBridgeArgsTest {

    private fun config(
        domain: String = "v.example.com",
        publicKey: String = "pk",
        dnsttCompat: Boolean = false
    ) = TunnelAdapterConfig.Vaydns(
        domain = domain,
        publicKey = publicKey,
        resolvers = emptyList(),
        effectiveDnsServer = "1.1.1.1:53",
        listenPort = 1082,
        listenHost = "127.0.0.1",
        maxPayload = 0,
        resolverMode = "roundrobin",
        rrSpreadCount = 3,
        dnsttCompat = dnsttCompat,
        recordType = "cname",
        maxQnameLen = 90,
        rps = 4.5,
        idleTimeoutSec = 12,
        keepaliveSec = 5,
        udpTimeoutMs = 700,
        maxNumLabels = 8,
        clientIdSize = 2
    )

    @Test
    fun `resolves every vaydns field to the bridge args`() {
        val a = VaydnsBridgeArgs.resolve(config())
        assertEquals("1.1.1.1:53", a.dnsServer)
        assertEquals("v.example.com", a.tunnelDomain)
        assertEquals("pk", a.publicKey)
        assertEquals(1082, a.listenPort)
        assertEquals("127.0.0.1", a.listenHost)
        assertFalse(a.dnsttCompat)
        assertEquals(0, a.maxPayload)
        assertEquals("cname", a.recordType)
        assertEquals(90, a.maxQnameLen)
        assertEquals(4.5, a.rps, 0.0)
        assertEquals(12, a.idleTimeout)
        assertEquals(5, a.keepalive)
        assertEquals(700, a.udpTimeout)
        assertEquals(8, a.maxNumLabels)
        assertEquals(2, a.clientIdSize)
        assertEquals("roundrobin", a.resolverMode)
        assertEquals(3, a.rrSpreadCount)
    }

    @Test
    fun `resolves dnstt compat flag`() {
        assertTrue(VaydnsBridgeArgs.resolve(config(dnsttCompat = true)).dnsttCompat)
    }
}
