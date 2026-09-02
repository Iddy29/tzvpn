package com.vpntz.app.tunnel.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessBridgeArgsTest {

    private fun config(security: String = "tls") = TunnelAdapterConfig.Vless(
        host = "v.example.com",
        port = 2053,
        uuid = "uuid-1",
        security = security,
        transport = "ws",
        wsPath = "/ws",
        sni = "cdn.example.com",
        cdnIp = "1.2.3.4",
        cdnPort = 443,
        sniFragmentEnabled = true,
        sniFragmentStrategy = "multi",
        sniFragmentDelayMs = 250,
        sniSpoofTtl = 12,
        fakeDecoyHost = "www.google.com",
        tcpMaxSeg = 70,
        chPaddingEnabled = false,
        wsHeaderObfuscation = true,
        wsPaddingEnabled = true,
        realityPubKey = "REALITY_PK",
        realityShortId = "16ab",
        realityFp = "chrome",
        listenPort = 1080,
        listenHost = "127.0.0.1"
    )

    @Test
    fun `reality is selected by security flag`() {
        assertFalse(VlessBridgeArgs.isReality(config("tls")))
        assertTrue(VlessBridgeArgs.isReality(config("reality")))
    }

    @Test
    fun `cdn args map every vless field`() {
        val a = VlessBridgeArgs.resolveCdn(config())
        assertEquals("1.2.3.4", a.cdnIp)
        assertEquals(443, a.cdnPort)
        assertEquals("v.example.com", a.serverDomain)
        assertEquals("uuid-1", a.vlessUuid)
        assertEquals("tls", a.security)
        assertEquals("ws", a.transport)
        assertEquals("/ws", a.wsPath)
        assertTrue(a.fragmentEnabled)
        assertEquals("multi", a.fragmentStrategy)
        assertEquals(250, a.fragmentDelayMs)
        assertEquals(12, a.sniSpoofTtl)
        assertEquals("www.google.com", a.fakeDecoyHost)
        assertEquals(70, a.tcpMaxSeg)
        assertEquals("cdn.example.com", a.vlessSni)
        assertFalse(a.chPaddingEnabled)
        assertTrue(a.wsHeaderObfuscation)
        assertTrue(a.wsPaddingEnabled)
    }

    @Test
    fun `reality args fall back to domain and cdnIp`() {
        val a = VlessBridgeArgs.resolveReality(config("reality"))
        assertEquals("1.2.3.4", a.serverHost) // cdnIp set
        assertEquals(443, a.serverPort)
        assertEquals("uuid-1", a.uuid)
        assertEquals("cdn.example.com", a.sni) // sni set
        assertEquals("REALITY_PK", a.publicKey)
        assertEquals("16ab", a.shortId)
        assertEquals("chrome", a.fingerprint)

        // fall back to host when cdnIp/sni blank
        val blank = config("reality").copy(cdnIp = "", sni = "")
        val b = VlessBridgeArgs.resolveReality(blank)
        assertEquals("v.example.com", b.serverHost)
        assertEquals("v.example.com", b.sni)
    }
}
