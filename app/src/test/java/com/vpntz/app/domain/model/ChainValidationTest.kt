package com.vpntz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainValidationTest {

    private fun profile(
        name: String,
        type: TunnelType
    ): ServerProfile = ServerProfile(name = name, tunnelType = type)

    // --- validate: arity rules ---

    @Test
    fun `chain with fewer than two profiles is rejected`() {
        assertNotNull(ChainValidation.validate(emptyList()))
        assertNotNull(ChainValidation.validate(listOf(profile("a", TunnelType.DNSTT))))
    }

    @Test
    fun `chain with more than four layers is rejected`() {
        val chain = List(5) { profile("p$it", TunnelType.SSH) }
        assertNotNull(ChainValidation.validate(chain))
    }

    // --- validate: chainable types ---

    @Test
    fun `combo tunnel types cannot be used in a chain`() {
        val combos = listOf(
            TunnelType.DNSTT_SSH, TunnelType.SLIPSTREAM_SSH,
            TunnelType.VAYDNS_SSH, TunnelType.NOIZDNS_SSH, TunnelType.NAIVE_SSH
        )
        for (combo in combos) {
            val chain = listOf(profile("a", TunnelType.DNSTT), profile("b", combo))
            assertNotNull("expected rejection for $combo", ChainValidation.validate(chain))
        }
    }

    // --- validate: bridge group uniqueness ---

    @Test
    fun `two profiles sharing a bridge group are rejected`() {
        val chain = listOf(profile("a", TunnelType.DNSTT), profile("b", TunnelType.NOIZDNS))
        val error = ChainValidation.validate(chain)
        assertNotNull(error)
        assertTrue(error!!.contains("same tunnel type"))
    }

    @Test
    fun `multiple SSH profiles are allowed`() {
        val chain = List(4) { profile("ssh$it", TunnelType.SSH) }
        assertNull(ChainValidation.validate(chain))
    }

    @Test
    fun `multiple SOCKS5 profiles are allowed`() {
        val chain = List(3) { profile("s$it", TunnelType.SOCKS5) }
        assertNull(ChainValidation.validate(chain))
    }

    // --- validate: layer connectivity ---

    @Test
    fun `dnstt to ssh chain is valid`() {
        val chain = listOf(profile("tunnel", TunnelType.DNSTT), profile("shell", TunnelType.SSH))
        assertNull(ChainValidation.validate(chain))
    }

    @Test
    fun `slipstream cannot accept an upstream layer`() {
        val chain = listOf(profile("tunnel", TunnelType.DNSTT), profile("kitonga", TunnelType.SLIPSTREAM))
        val error = ChainValidation.validate(chain)
        assertNotNull(error)
        assertTrue(error!!.contains("cannot connect through"))
    }

    @Test
    fun `doh to snowflake chain is valid (tor over doh)`() {
        val chain = listOf(profile("dns", TunnelType.DOH), profile("tor", TunnelType.SNOWFLAKE))
        assertNull(ChainValidation.validate(chain))
    }

    @Test
    fun `four layer mixed chain is valid`() {
        val chain = listOf(
            profile("tunnel", TunnelType.DNSTT),
            profile("shell", TunnelType.SSH),
            profile("socks", TunnelType.SOCKS5),
            profile("dns", TunnelType.DOH)
        )
        assertNull(ChainValidation.validate(chain))
    }

    // --- outputType / canConsumeInput matrix ---

    @Test
    fun `every chainable intermediate type outputs SOCKS5`() {
        for (type in ChainValidation.CAN_BE_INTERMEDIATE) {
            assertEquals("expected SOCKS5 output for $type", LayerOutput.SOCKS5, ChainValidation.outputType(type))
        }
    }

    @Test
    fun `combo types have no chain output`() {
        assertNull(ChainValidation.outputType(TunnelType.DNSTT_SSH))
        assertNull(ChainValidation.outputType(TunnelType.VLESS))
        assertNull(ChainValidation.outputType(TunnelType.HYSTERIA2))
    }

    @Test
    fun `ssh and socks5 consume any input`() {
        for (input in LayerOutput.entries) {
            assertTrue(ChainValidation.canConsumeInput(TunnelType.SSH, input))
            assertTrue(ChainValidation.canConsumeInput(TunnelType.SOCKS5, input))
        }
    }

    @Test
    fun `slipstream consumes no upstream input`() {
        for (input in LayerOutput.entries) {
            assertFalse(ChainValidation.canConsumeInput(TunnelType.SLIPSTREAM, input))
        }
    }

    @Test
    fun `dns-based and tor layers require SOCKS5 input`() {
        for (type in listOf(TunnelType.DNSTT, TunnelType.NOIZDNS, TunnelType.VAYDNS, TunnelType.DOH, TunnelType.SNOWFLAKE)) {
            assertTrue(ChainValidation.canConsumeInput(type, LayerOutput.SOCKS5))
            assertFalse(ChainValidation.canConsumeInput(type, LayerOutput.RAW_TCP))
        }
    }

    // --- bridge groups ---

    @Test
    fun `bridge group mapping is stable`() {
        assertEquals("dnstt", ChainValidation.bridgeGroup(TunnelType.DNSTT))
        assertEquals("dnstt", ChainValidation.bridgeGroup(TunnelType.NOIZDNS))
        assertEquals("vaydns", ChainValidation.bridgeGroup(TunnelType.VAYDNS))
        assertEquals("slipstream", ChainValidation.bridgeGroup(TunnelType.SLIPSTREAM))
        assertEquals("ssh", ChainValidation.bridgeGroup(TunnelType.SSH))
        assertEquals("naive", ChainValidation.bridgeGroup(TunnelType.NAIVE))
        assertEquals("snowflake", ChainValidation.bridgeGroup(TunnelType.SNOWFLAKE))
        assertEquals("doh", ChainValidation.bridgeGroup(TunnelType.DOH))
        assertEquals("socks5", ChainValidation.bridgeGroup(TunnelType.SOCKS5))
    }

    // --- needsVpnFirst ---

    @Test
    fun `only slipstream and snowflake can start before vpn`() {
        assertFalse(ChainValidation.needsVpnFirst(TunnelType.SLIPSTREAM))
        assertFalse(ChainValidation.needsVpnFirst(TunnelType.SNOWFLAKE))
        for (type in TunnelType.entries - TunnelType.SLIPSTREAM - TunnelType.SNOWFLAKE) {
            assertTrue("expected VPN-first for $type", ChainValidation.needsVpnFirst(type))
        }
    }
}
