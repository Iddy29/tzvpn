package com.vpntz.app.tunnel.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Hysteria2BridgeArgsTest {

    private fun config(host: String = "hy.example.com", sni: String = "sni.example") =
        TunnelAdapterConfig.Hysteria2(
            host = host,
            port = 443,
            password = "pw",
            sni = sni,
            insecure = false,
            obfs = "salamander",
            obfsPassword = "op",
            listenPort = 1080,
            listenHost = "127.0.0.1"
        )

    @Test
    fun `resolves hysteria2 config to bridge args`() {
        val a = Hysteria2BridgeArgs.resolve(config())
        assertEquals("127.0.0.1", a.listenHost)
        assertEquals(1080, a.listenPort)
        assertEquals("hy.example.com", a.serverHost)
        assertEquals(443, a.serverPort)
        assertEquals("pw", a.password)
        assertEquals("sni.example", a.sni)
        assertFalse(a.insecure)
        assertEquals("salamander", a.obfs)
        assertEquals("op", a.obfsPassword)
    }

    @Test
    fun `sni falls back to server host when blank`() {
        val a = Hysteria2BridgeArgs.resolve(config(sni = ""))
        assertEquals("hy.example.com", a.sni)
        assertTrue(a.sni.isNotBlank())
    }
}
