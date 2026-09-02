package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.SshAuthType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshBridgeArgsTest {

    private fun base(
        wsEnabled: Boolean = false,
        httpProxyHost: String = ""
    ) = TunnelAdapterConfig.Ssh(
        host = "s.example.com",
        port = 22,
        username = "root",
        authType = "KEY",
        password = "",
        privateKey = "PEM",
        keyPassphrase = "phrase",
        listenPort = 1080,
        listenHost = "127.0.0.1",
        forwardDnsThroughSsh = true,
        remoteDnsHost = "8.8.8.8",
        remoteDnsFallback = "1.1.1.1",
        payload = "rawpayload",
        tlsEnabled = true,
        tlsSni = "sni.example",
        httpProxyHost = httpProxyHost,
        httpProxyPort = 8080,
        wsEnabled = wsEnabled,
        wsPath = "/",
        wsUseTls = true,
        wsCustomHost = "ws.example",
        wsTlsSni = "wss.example"
    )

    @Test
    fun `direct transport selected by default`() {
        val a = SshBridgeArgs.resolve(base())
        assertEquals(SshTransport.DIRECT, a.transport)
        assertEquals("s.example.com", a.sshHost)
        assertEquals(22, a.sshPort)
        assertEquals("root", a.sshUsername)
        assertEquals(SshAuthType.KEY, a.sshAuthType)
        assertEquals("PEM", a.sshPrivateKey)
        assertTrue(a.forwardDnsThroughSsh)
        assertEquals("rawpayload", a.sshPayload)
    }

    @Test
    fun `websocket transport selected when ws enabled`() {
        val a = SshBridgeArgs.resolve(base(wsEnabled = true))
        assertEquals(SshTransport.WEB_SOCKET, a.transport)
        assertEquals("/", a.wsPath)
        assertTrue(a.wsUseTls)
        assertEquals("ws.example", a.wsCustomHost)
        assertEquals("wss.example", a.wsTlsSni)
    }

    @Test
    fun `http proxy transport selected when proxy host is set`() {
        val a = SshBridgeArgs.resolve(base(httpProxyHost = "proxy.example"))
        assertEquals(SshTransport.HTTP_PROXY, a.transport)
        assertEquals("proxy.example", a.proxyHost)
        assertEquals(8080, a.proxyPort)
    }

    @Test
    fun `unknown auth type falls back to password`() {
        val a = SshBridgeArgs.resolve(base().copy(authType = "totally-unknown"))
        assertEquals(SshAuthType.PASSWORD, a.sshAuthType)
        assertFalse(a.sshPassword.isNotEmpty())
    }
}
