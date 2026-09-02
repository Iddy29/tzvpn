package com.vpntz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileTest {

    // --- isExpired invariant ---

    @Test
    fun `zero expiration date means never expires`() {
        val profile = ServerProfile(name = "p", expirationDate = 0)
        assertFalse(profile.isExpired)
    }

    @Test
    fun `future expiration date is not expired`() {
        val profile = ServerProfile(name = "p", expirationDate = System.currentTimeMillis() + 60_000)
        assertFalse(profile.isExpired)
    }

    @Test
    fun `past expiration date is expired`() {
        val profile = ServerProfile(name = "p", expirationDate = System.currentTimeMillis() - 60_000)
        assertTrue(profile.isExpired)
    }

    // --- isAvailable feature-flag mapping ---

    @Test
    fun `snowflake availability follows the tor flag`() {
        assertTrue(TunnelType.SNOWFLAKE.isAvailable(includeTor = true, includeNaive = false))
        assertFalse(TunnelType.SNOWFLAKE.isAvailable(includeTor = false, includeNaive = true))
    }

    @Test
    fun `naive variants follow the naive flag`() {
        for (type in listOf(TunnelType.NAIVE, TunnelType.NAIVE_SSH)) {
            assertTrue(type.isAvailable(includeTor = false, includeNaive = true))
            assertFalse(type.isAvailable(includeTor = true, includeNaive = false))
        }
    }

    @Test
    fun `all other types are always available`() {
        val alwaysAvailable = TunnelType.entries - TunnelType.SNOWFLAKE - TunnelType.NAIVE - TunnelType.NAIVE_SSH
        for (type in alwaysAvailable) {
            assertTrue(type.isAvailable(includeTor = false, includeNaive = false))
        }
    }

    // --- enum fromValue fallbacks ---

    @Test
    fun `tunnel type fromValue resolves known values and falls back to DNSTT`() {
        assertEquals(TunnelType.VAYDNS, TunnelType.fromValue("vaydns"))
        assertEquals(TunnelType.NOIZDNS, TunnelType.fromValue("sayedns"))
        assertEquals(TunnelType.SLIPSTREAM, TunnelType.fromValue("slipstream"))
        assertEquals(TunnelType.HYSTERIA2, TunnelType.fromValue("hysteria2"))
        assertEquals(TunnelType.DNSTT, TunnelType.fromValue("nonexistent"))
    }

    @Test
    fun `congestion control fromValue falls back to BBR`() {
        assertEquals(CongestionControl.DCUBIC, CongestionControl.fromValue("dcubic"))
        assertEquals(CongestionControl.BBR, CongestionControl.fromValue("bbr"))
        assertEquals(CongestionControl.BBR, CongestionControl.fromValue("whatever"))
    }

    @Test
    fun `ssh auth type fromValue falls back to PASSWORD`() {
        assertEquals(SshAuthType.KEY, SshAuthType.fromValue("key"))
        assertEquals(SshAuthType.PASSWORD, SshAuthType.fromValue("password"))
        assertEquals(SshAuthType.PASSWORD, SshAuthType.fromValue("unknown"))
    }

    @Test
    fun `resolver mode fromValue falls back to FANOUT`() {
        assertEquals(ResolverMode.ROUND_ROBIN, ResolverMode.fromValue("roundrobin"))
        assertEquals(ResolverMode.FANOUT, ResolverMode.fromValue("fanout"))
        assertEquals(ResolverMode.FANOUT, ResolverMode.fromValue("unknown"))
    }

    @Test
    fun `dns transport fromValue falls back to UDP`() {
        assertEquals(DnsTransport.DOT, DnsTransport.fromValue("dot"))
        assertEquals(DnsTransport.DOH, DnsTransport.fromValue("doh"))
        assertEquals(DnsTransport.UDP, DnsTransport.fromValue("unknown"))
    }

    // --- defaults (behavioral contract relied on by mappers/importers) ---

    @Test
    fun `new profile defaults match the documented baseline`() {
        val profile = ServerProfile(name = "p")
        assertEquals(0L, profile.id)
        assertEquals(TunnelType.DNSTT, profile.tunnelType)
        assertEquals("127.0.0.1", profile.tcpListenHost)
        assertEquals(1080, profile.tcpListenPort)
        assertEquals(DnsTransport.UDP, profile.dnsTransport)
        assertEquals(SshAuthType.PASSWORD, profile.sshAuthType)
        assertEquals(0, profile.expirationDate)
        assertNull(profile.socksUsername)
        assertNull(profile.socksPassword)
    }
}
