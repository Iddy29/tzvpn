package com.vpntz.app.tunnel.adapter

import com.vpntz.app.domain.model.DnsResolver
import com.vpntz.app.domain.model.ServerProfile
import com.vpntz.app.domain.model.TunnelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelConfigMapperTest {

    private val mapper = TunnelConfigMapper()
    private val runtime = TunnelRuntimeDefaults(listenPort = 1080, listenHost = "127.0.0.1")

    // ── valid translations ──────────────────────────────────────────────────

    @Test
    fun `dnstt maps profile fields and defaults`() {
        val p = profile(TunnelType.DNSTT, domain = "t.example.com", pubKey = "abc123")
        val cfg = mapper.map(TunnelType.DNSTT, p, runtime) as TunnelAdapterConfig.Dnstt
        assertEquals("t.example.com", cfg.domain)
        assertEquals("abc123", cfg.publicKey)
        assertEquals(1080, cfg.listenPort)
        assertEquals("127.0.0.1", cfg.listenHost)
        assertFalse(cfg.noizdns)
        assertEquals(p.resolverMode.value, cfg.resolverMode)
    }

    @Test
    fun `noizdns maps with noiz flags on`() {
        val p = profile(TunnelType.NOIZDNS, domain = "t.example.com", pubKey = "k").copy(noizdnsStealth = true)
        val cfg = mapper.map(TunnelType.NOIZDNS, p, runtime) as TunnelAdapterConfig.Dnstt
        assertTrue(cfg.noizdns)
        assertTrue(cfg.noizStealth)
    }

    @Test
    fun `vaydns maps advanced fields`() {
        val p = profile(TunnelType.VAYDNS, domain = "t.example.com", pubKey = "k")
            .copy(vaydnsDnsttCompat = true, vaydnsRecordType = "cname", vaydnsRps = 3.5, vaydnsMaxQnameLen = 77)
        val cfg = mapper.map(TunnelType.VAYDNS, p, runtime) as TunnelAdapterConfig.Vaydns
        assertTrue(cfg.dnsttCompat)
        assertEquals("cname", cfg.recordType)
        assertEquals(3.5, cfg.rps, 0.0)
        assertEquals(77, cfg.maxQnameLen)
    }

    @Test
    fun `slipstream maps resolvers and debug flag`() {
        val p = profile(TunnelType.SLIPSTREAM, domain = "t.example.com")
        val cfg = mapper.map(TunnelType.SLIPSTREAM, p, runtime.copy(resolvers = listOf(DnsResolver("1.1.1.1")), debugLogging = true)) as TunnelAdapterConfig.Slipstream
        assertEquals(1, cfg.resolvers.size)
        assertTrue(cfg.debugLogging)
        assertEquals("1.1.1.1", cfg.resolvers.first().host)
    }

    @Test
    fun `naive maps host port and credentials`() {
        val p = profile(TunnelType.NAIVE, domain = "n.example.com").copy(naivePort = 8443, naiveUsername = "u", naivePassword = "pw")
        val cfg = mapper.map(TunnelType.NAIVE, p, runtime) as TunnelAdapterConfig.Naive
        assertEquals("n.example.com", cfg.host)
        assertEquals(8443, cfg.port)
        assertEquals("u", cfg.username)
        assertEquals("pw", cfg.password)
    }

    @Test
    fun `vless maps cdn and sni fragment fields`() {
        val p = profile(TunnelType.VLESS, domain = "v.example.com").copy(
            vlessUuid = "uuid-1", cdnIp = "1.2.3.4", cdnPort = 2053,
            sniFragmentStrategy = "multi", wsHeaderObfuscation = false
        )
        val cfg = mapper.map(TunnelType.VLESS, p, runtime) as TunnelAdapterConfig.Vless
        assertEquals("uuid-1", cfg.uuid)
        assertEquals("1.2.3.4", cfg.cdnIp)
        assertEquals(2053, cfg.cdnPort)
        assertEquals("multi", cfg.sniFragmentStrategy)
        assertFalse(cfg.wsHeaderObfuscation)
        assertTrue(cfg.sniFragmentEnabled)
    }

    @Test
    fun `ssh maps auth and transports`() {
        val p = profile(TunnelType.SSH, domain = "s.example.com").copy(
            sshPort = 2222, sshUsername = "root", sshAuthType = com.vpntz.app.domain.model.SshAuthType.KEY,
            sshPrivateKey = "PEM", sshTlsEnabled = true, sshWsEnabled = true
        )
        val cfg = mapper.map(TunnelType.SSH, p, runtime) as TunnelAdapterConfig.Ssh
        assertEquals("s.example.com", cfg.host)
        assertEquals(2222, cfg.port)
        assertEquals("KEY", cfg.authType)
        assertEquals("PEM", cfg.privateKey)
        assertTrue(cfg.tlsEnabled)
        assertTrue(cfg.wsEnabled)
    }

    @Test
    fun `doh maps url host`() {
        val p = profile(TunnelType.DOH, domain = "d.example.com")
        val cfg = mapper.map(TunnelType.DOH, p, runtime) as TunnelAdapterConfig.Doh
        assertEquals("d.example.com", cfg.url)
    }

    @Test
    fun `hysteria2 maps quic options`() {
        val p = profile(TunnelType.HYSTERIA2, domain = "h.example.com").copy(
            hy2Password = "pw", hy2Sni = "sn", hy2Insecure = true, hy2Obfs = "salamander", hy2ObfsPassword = "op"
        )
        val cfg = mapper.map(TunnelType.HYSTERIA2, p, runtime) as TunnelAdapterConfig.Hysteria2
        assertEquals("pw", cfg.password)
        assertTrue(cfg.insecure)
        assertEquals("salamander", cfg.obfs)
        assertEquals("op", cfg.obfsPassword)
    }

    @Test
    fun `snowflake maps bridge lines`() {
        val p = profile(TunnelType.SNOWFLAKE, domain = "").copy(torBridgeLines = "obfs4 1.2.3.4:443 123 cert=..")
        val cfg = mapper.map(TunnelType.SNOWFLAKE, p, runtime) as TunnelAdapterConfig.Snowflake
        assertEquals(p.torBridgeLines, cfg.bridges)
    }

    // ── invalid configuration ───────────────────────────────────────────────

    @Test
    fun `dnstt rejects blank public key`() {
        val p = profile(TunnelType.DNSTT, domain = "t.example.com", pubKey = "")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.DNSTT, p, runtime) }
    }

    @Test
    fun `noizdns rejects blank public key and blank domain`() {
        val noKey = profile(TunnelType.NOIZDNS, domain = "t.example.com", pubKey = "")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.NOIZDNS, noKey, runtime) }
        val noDomain = profile(TunnelType.NOIZDNS, domain = "", pubKey = "k")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.NOIZDNS, noDomain, runtime) }
    }

    @Test
    fun `vaydns rejects blank public key and blank domain`() {
        val noKey = profile(TunnelType.VAYDNS, domain = "v.example.com", pubKey = "")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.VAYDNS, noKey, runtime) }
        val noDomain = profile(TunnelType.VAYDNS, domain = "", pubKey = "k")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.VAYDNS, noDomain, runtime) }
    }

    @Test
    fun `slipstream rejects blank domain`() {
        val p = profile(TunnelType.SLIPSTREAM, domain = "")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.SLIPSTREAM, p, runtime) }
    }

    @Test
    fun `vless rejects blank uuid`() {
        val p = profile(TunnelType.VLESS, domain = "v.example.com").copy(vlessUuid = "")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.VLESS, p, runtime) }
    }

    @Test
    fun `socks5 is not a tunnel adapter`() {
        val p = profile(TunnelType.SOCKS5, domain = "")
        assertThrows(IllegalArgumentException::class.java) { mapper.map(TunnelType.SOCKS5, p, runtime) }
    }

    private fun profile(type: TunnelType, domain: String, pubKey: String = "k"): ServerProfile =
        ServerProfile(name = "p", domain = domain, tunnelType = type, dnsttPublicKey = pubKey)
}
